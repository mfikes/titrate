(require '[cljs.build.api]
         '[clojure.java.io :as io])

(cljs.build.api/build "bootstrap-src/bootstrap-runner"
  {:main       'runner.bootstrap
   :output-to  "out/main.js"
   :output-dir "out"
   :target     :nodejs})

(defn copy-source
  [filename]
  (spit (str "out/" filename)
    (slurp (io/resource filename))))

(copy-source "cljs/test.cljc")
(copy-source "cljs/analyzer/api.cljc")
(copy-source "clojure/template.clj")
