(ns refactor-nrepl.util-test
  (:require [refactor-nrepl.util :refer :all]
            [clojure.test :refer :all]))

(def file-content (slurp "test/resources/testproject/src/com/example/sexp_test.clj"))
(def weird-file-content ";; some weird file
;; not even clojure
;; perhaps? no parens!")
(def binding-location [4 10])
(def funcall-location [7 8])
(def set-location [8 35])
(def map-location [8 28])
(def weird-location [2 5])

(deftest get-enclosing-sexp-test
  (is (= (apply get-enclosing-sexp file-content binding-location)
         "[some :bindings
        more :bindings]"))
  (is (= (apply get-enclosing-sexp file-content funcall-location)
         "(println #{some}
             ;; helpful comment
             (prn {\"foo\" {:qux [#{more}]}}))"))
  (is (= (apply get-enclosing-sexp file-content set-location) "#{more}"))
  (is (= (apply get-enclosing-sexp file-content map-location) "{:qux [#{more}]}"))
  (is (= (apply get-enclosing-sexp weird-file-content weird-location) "")))
