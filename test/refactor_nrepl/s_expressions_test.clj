(ns refactor-nrepl.s-expressions-test
  (:require
   [clojure.test :as test]
   [refactor-nrepl.s-expressions :as sut]))

(def file-content (slurp "testproject/src/com/example/sexp_test.clj"))

(def weird-file-content ";; some weird file
  ;; not even clojure
  ;; perhaps? no parens!")

(def file-content-with-set ";; with set
  #{foo bar baz}
  ;; some other stuff
  (foobar baz)")

(def file-content-with-uneval "#_ foo
(foobar baz)")

(def binding-location [3 8])
(def set-location [7 35])
(def map-location [7 28])
(def weird-location [1 5])
(def println-location [5 8])
(def when-not-location [10 9])

(test/deftest get-enclosing-sexp-test
  (test/is (= "[some :bindings
        more :bindings]"
              (apply sut/get-enclosing-sexp file-content binding-location)))
  (test/is (=  "(println #{some}
             ;; unhelpful comment )
             (prn {\"foo\" {:qux [#{more}]}}))"
               (apply sut/get-enclosing-sexp file-content println-location)))
  (test/is (=  "#{more}" (apply sut/get-enclosing-sexp file-content set-location)))
  (test/is (=  "{:qux [#{more}]}" (apply sut/get-enclosing-sexp file-content map-location)))
  (test/is (=  nil (apply sut/get-enclosing-sexp weird-file-content weird-location)))
  (test/is (= "(when-not (= true true)
    (= 5 (* 2 2)))"
              (apply sut/get-enclosing-sexp file-content when-not-location)))
  (test/is (= nil (sut/get-first-sexp weird-file-content)))
  (test/is (=  "#{foo bar baz}" (sut/get-first-sexp file-content-with-set))))

(test/deftest get-first-sexp
  (test/is (= "(ns com.example.sexp-test)"
              (sut/get-first-sexp file-content)))
  (test/is (= "(foobar baz)"
              (sut/get-first-sexp file-content-with-uneval))))
