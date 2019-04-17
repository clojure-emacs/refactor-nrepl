(ns refactor-nrepl.s-expressions-test
  (:require [refactor-nrepl.s-expressions :as sut]
            [clojure.test :as t]))

(def file-content (slurp "test/resources/testproject/src/com/example/sexp_test.clj"))
(def weird-file-content ";; some weird file
  ;; not even clojure
  ;; perhaps? no parens!")
(def file-content-with-set ";; with set
  #{foo bar baz}
  ;; some other stuff
  (foobar baz)")
(def binding-location [3 8])
(def set-location [7 35])
(def map-location [7 28])
(def weird-location [1 5])
(def println-location [5 8])
(def when-not-location [10 9])

(t/deftest get-enclosing-sexp-test
  (t/is (= "[some :bindings
        more :bindings]"
           (apply sut/get-enclosing-sexp file-content binding-location)))
  (t/is (=  "(println #{some}
             ;; unhelpful comment )
             (prn {\"foo\" {:qux [#{more}]}}))"
            (apply sut/get-enclosing-sexp file-content println-location)))
  (t/is (=  "#{more}" (apply sut/get-enclosing-sexp file-content set-location)))
  (t/is (=  "{:qux [#{more}]}" (apply sut/get-enclosing-sexp file-content map-location)))
  (t/is (=  nil (apply sut/get-enclosing-sexp weird-file-content weird-location)))
  (t/is (= "(when-not (= true true)
    (= 5 (* 2 2)))"
           (apply sut/get-enclosing-sexp file-content when-not-location)))
  (t/is (= nil (sut/get-first-sexp weird-file-content)))
  (t/is (=  "#{foo bar baz}" (sut/get-first-sexp file-content-with-set))))
