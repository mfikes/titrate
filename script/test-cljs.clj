(require '[cljs.build.api]
         '[cljs.test])

;; We define a custom assert so that 
;; (is (char? nil)) produces nice output
;; See http://blog.fikesfarm.com/posts/2016-02-25-custom-test-asserts-in-planck.html

(defmethod cljs.test/assert-expr 'char? 
  [menv msg form]
  (let [arg    (second form)
        result (and (not (nil? arg))
                    (char? arg))]
    `(do
       (if ~result
         (cljs.test/do-report
           {:type     :pass
            :message  ~msg
            :expected '~form
            :actual   (list '~'char? ~arg)})
         (cljs.test/do-report
           {:type     :fail
            :message  ~msg
            :expected '~form
            :actual   (list '~'not 
                        (list '~'char? ~arg))}))
       ~result)))

(cljs.build.api/build "src"
  {:main       'runner.core
   :output-to  "out/main.js"
   :output-dir "out"
   :target     :nodejs})
