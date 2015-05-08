(ns refactor-nrepl.util-test
  (:require [refactor-nrepl.util :refer :all]
            [clojure.test :refer :all]))

(def file-content (slurp "test/resources/testproject/src/com/example/sexp_test.clj"))
(def binding-location [4 10])
(def funcall-location [7 8])
(def set-location [7 35])
(def map-location [7 28])

(deftest get-enclosing-sexp-test
  (is (= (apply get-enclosing-sexp file-content binding-location)
         '[some :bindings
           more :bindings]))
  (is (= (apply get-enclosing-sexp file-content funcall-location)
         '(println #{some}
                   (prn {"foo" {:qux [#{more}]}}))))
  (is (= (apply get-enclosing-sexp file-content set-location) #{'more}))
  (is (= (apply get-enclosing-sexp file-content map-location) {:qux [#{'more}]})))
