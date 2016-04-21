(ns runner.core
  (:require #?(:clj  [clojure.test :refer [run-tests]]
               :cljs [cljs.test :refer-macros [run-tests]])
            [foo.core-test]))

(defn -main [& args]
  (run-tests 'foo.core-test))

;; Set up things for Node

#?(:cljs
  (defn setup-for-node []
    (set! *print-newline* false)
    (set! *print-fn*
      (fn [& args]
        (.apply (.-log js/console) js/console (into-array args))))
    (set! *main-cli-fn* -main)))

#?(:cljs (setup-for-node))
