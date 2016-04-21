(ns runner.bootstrap
  (:require [clojure.string :as string]
            [cljs.nodejs :as nodejs]
            [cljs.js :as cljs]
            [cljs.reader :as reader]))

(def out-dir "out")

(def src-paths [out-dir
                "src/main"
                "src/test"
                "bootstrap-src/custom-asserts"])

(defn init-runtime 
  "Initializes the runtime so that we can use the cljs.user
  namespace and so that Google Closure is set up to work
  properly with :optimizations :none."
  []
  (set! (.-user js/cljs) #js {})
  ;; monkey-patch isProvided_ to avoid useless warnings
  (js* "goog.isProvided_ = function(x) { return false; };")
  ;; monkey-patch goog.require, skip all the loaded checks
  (set! (.-require js/goog)
    (fn [name]
      (js/CLOSURE_IMPORT_SCRIPT
        (aget (.. js/goog -dependencies_ -nameToPath) name))))
  ;; setup printing
  (nodejs/enable-util-print!)
  ;; redef goog.require to track loaded libs
  (set! *loaded-libs* #{"cljs.core"})
  (set! (.-require js/goog)
    (fn [name reload]
      (when (or (not (contains? *loaded-libs* name)) reload)
        (set! *loaded-libs* (conj (or *loaded-libs* #{}) name))
        (js/CLOSURE_IMPORT_SCRIPT
          (aget (.. js/goog -dependencies_ -nameToPath) name))))))

;; Node file reading fns

(def fs (nodejs/require "fs"))

(defn node-read-file
  "Accepts a filename to read and a callback. Upon success, invokes
  callback with the source. Otherwise invokes the callback with nil."
  [filename cb]
  (.readFile fs filename "utf-8"
    (fn [err source]
      (cb (when-not err
            source)))))

(defn node-read-file-sync
  "Accepts a filename to read. Upon success, returns the source.
  Otherwise returns nil."
  [filename]
  (.readFileSync fs filename "utf-8"))

;; Facilities for loading Closure deps

(defn closure-index
  "Builds an index of Closure files. Similar to 
  cljs.js-deps/goog-dependencies*"
  []
  (let [paths-to-provides
        (map (fn [[_ path provides]]
               [path (map second
                       (re-seq #"'(.*?)'" provides))])
          (re-seq #"\ngoog\.addDependency\('(.*)', \[(.*?)\].*"
            (node-read-file-sync (str out-dir "/goog/deps.js"))))]
    (into {}
      (for [[path provides] paths-to-provides
            provide provides]
        [(symbol provide) (str out-dir "/goog/" (second (re-find #"(.*)\.js$" path)))]))))

(def closure-index-mem (memoize closure-index))

(defn load-goog
  "Loads a Google Closure implementation source file."
  [name cb]
  (if-let [goog-path (get (closure-index-mem) name)]
    (if-let [source (node-read-file-sync (str goog-path ".js"))]
      (cb {:source source
           :lang   :js})
      (cb nil))
    (cb nil)))

;; Facilities for loading files

(defn- filename->lang
  "Converts a filename to a lang keyword by inspecting the file
  extension."
  [filename]
  (if (string/ends-with? filename ".js")
    :js
    :clj))

(defn replace-extension
  "Replaces the extension on a file."
  [filename new-extension]
  (string/replace filename #".clj[sc]?$" new-extension))

(defn parse-edn
  "Parses edn source to Clojure data."
  [edn-source]
  (reader/read-string edn-source))

(defn- read-some
  "Reads the first filename in a sequence of supplied filenames,
  using a supplied read-file-fn, calling back upon first successful
  read, otherwise calling back with nil. Before calling back, first
  attempts to read AOT artifacts (JavaScript and cache edn)."
  [[filename & more-filenames] read-file-fn cb]
  (if filename
    (read-file-fn
      filename
      (fn [source]
        (if source
          (let [source-cb-value {:lang   (filename->lang filename)
                                 :source source}]
            (if (or (string/ends-with? filename ".cljs")
                    (string/ends-with? filename ".cljc"))
              (read-file-fn
                (replace-extension filename ".js")
                (fn [javascript-source]
                  (if javascript-source
                    (read-file-fn
                      (str filename ".cache.edn")
                      (fn [cache-edn]
                        (if cache-edn
                          (cb {:lang   :js
                               :source javascript-source
                               :cache  (parse-edn cache-edn)})
                          (cb source-cb-value))))
                    (cb source-cb-value))))
              (cb source-cb-value)))
          (read-some more-filenames read-file-fn cb))))
    (cb nil)))

(defn filenames-to-try
  "Produces a sequence of filenames to try reading, in the
  order they should be tried."
  [src-paths macros path]
  (let [extensions (if macros
                     [".clj" ".cljc"]
                     [".cljs" ".cljc" ".js"])]
    (for [extension extensions
          src-path  src-paths]
      (str src-path "/" path extension))))

(defn skip-load?
  "Indicates namespaces that we either don't need to load,
  shouldn't load, or cannot load (owing to unresolved
  technical issues)."
  [name macros]
  ((if macros
     #{'cljs.pprint
       'cljs.env.macros
       'cljs.analyzer.macros
       'cljs.compiler.macros}
     #{'goog.object
       'goog.string
       'goog.string.StringBuffer
       'goog.array
       'cljs.core
       'cljs.env
       'cljs.pprint
       'cljs.tools.reader}) name))

;; An atom to keep track of things we've already loaded
(def loaded (atom #{}))

(defn load?
  "Determines whether the given namespace should be loaded."
  [name macros]
  (let [do-not-load (or (@loaded [name macros])
                        (skip-load? name macros))]
    (swap! loaded conj [name macros])
    (not do-not-load)))

(defn make-load-fn
  "Makes a load function that will read from a sequence of src-paths
  using a supplied read-file-fn. It returns a cljs.js-compatible
  *load-fn*.
  Read-file-fn is a 2-arity function (fn [filename source-cb] ...) where
  source-cb is itself a function (fn [source] ...) that needs to be called
  with the source of the library (as string)."
  [src-paths read-file-fn]
  (fn [{:keys [name macros path]} cb]
    (if (load? name macros)
      (if (re-matches #"^goog/.*" path)
        (load-goog name cb)
        (read-some (filenames-to-try src-paths macros path) read-file-fn cb))
      (cb {:source ""
           :lang   :js}))))

;; Facilities for evaluating JavaScript

(def vm (nodejs/require "vm"))

(defn node-eval 
  "Evaluates JavaScript in node."
  [{:keys [name source]}]
  (if-not js/COMPILED
    (.runInThisContext vm source (str (munge name) ".js"))
    (js/eval source)))

;; Facilities for driving cljs.js

(def load-fn (make-load-fn src-paths node-read-file))

(defn eval-form 
  "Evaluates a supplied form in a given namespace,
  calling back with the evaluation result."
  [st ns form cb]
  (cljs/eval st
    form
    {:ns      ns
     :context :expr
     :load    load-fn
     :eval    node-eval
     :verbose false}
    cb))

;; For some reason, *eval-fn* is not staying set.
;; Work around here.
(set! cljs/*eval-fn* node-eval)

(defn run-tests 
  "Runs the tests."
  []
  (let [st (cljs/empty-state)]
    (eval-form st 'cljs.user
      '(ns bootstrap-runner.core
         (:require [custom-asserts.core]
                   [runner.core]))
      (fn [{:keys [value error]}]
        (if error
          (prn error)
          (eval-form st 'bootstrap-runner.core
            '(runner.core/-main)
            (fn [{:keys [value error]}]
              (when error
                (prn error)))))))))

(defn -main [& args]
  (init-runtime)
  (run-tests))

(set! *main-cli-fn* -main)
