(ns foo.core-test
  (:require #?(:clj  [clojure.test :refer [deftest is]]
               :cljs [cljs.test :refer-macros [deftest is]])
            [foo.core :refer [add-five get-scheme]]))

(deftest add-five-test
  (is (= 8 (add-five "3"))))

(deftest get-scheme-test
  (is (= "http" (get-scheme "http://foo.bar"))))

;; The following test should cause a failure that uses a custom
;; assert, logging an error that looks like:
;;
;;   expected: (char? nil)
;;     actual: (not (char? nil))
;; 
;; See http://blog.fikesfarm.com/posts/2016-02-25-custom-test-asserts-in-planck.html

(deftest custom-assert-test
  (is (char? nil)))
