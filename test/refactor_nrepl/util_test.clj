(ns refactor-nrepl.util-test
  (:require [clojure.test :refer :all]
            [refactor-nrepl.util :refer :all]))

(def file-content (slurp "test/resources/testproject/src/com/example/sexp_test.clj"))
  (def weird-file-content ";; some weird file
  ;; not even clojure
;; perhaps? no parens!")
  (def file-content-with-set ";; with set
  #{foo bar baz}
  ;; some other stuff
(foobar baz)")
(def binding-location [3 8])
(def funcall-location [6 8])
(def set-location [7 35])
(def map-location [7 28])
(def weird-location [1 5])
(def println-location [5 8])

(deftest get-enclosing-sexp-test
  (is (= (apply get-enclosing-sexp file-content binding-location)
         "[some :bindings
        more :bindings]"))
  (is (= (apply get-enclosing-sexp file-content println-location)
         "(println #{some}
             ;; helpful comment
             (prn {\"foo\" {:qux [#{more}]}}))"))
  (is (= (apply get-enclosing-sexp file-content set-location) "#{more}"))
  (is (= (apply get-enclosing-sexp file-content map-location) "{:qux [#{more}]}"))
  (is (= (apply get-enclosing-sexp weird-file-content weird-location) ""))
  (is (= (get-next-sexp weird-file-content) ""))
  (is (= (get-next-sexp file-content-with-set) "#{foo bar baz}")))

(deftest with-additional-ex-data-test
  (try
    (with-additional-ex-data [:foo :bar]
      (throw (ex-info "ok" {})))
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [foo]} (ex-data e)]
        (is (= foo :bar))))))
