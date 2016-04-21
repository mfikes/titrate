(ns foo.core
  (#?(:clj  :require 
      :cljs :require-macros)
    [foo.macros])
  (:import #?(:clj [java.net URI] :cljs [goog Uri])))

(defn str->int [s]
  #?(:clj  (Integer/parseInt s)
     :cljs (js/parseInt s)))

(defn add-five [s]
  (+ (str->int s) 
     (foo.macros/str->int "5")))

(defn get-scheme [url-str]
  #?(:clj  (.getScheme (URI. url-str))
     :cljs (.-scheme_ (Uri. url-str))))
