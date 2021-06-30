(ns refactor-nrepl.extract-definition-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [refactor-nrepl.extract-definition :refer :all]))

(defn- -extract-definition
  [name line col]
  (get-in (extract-definition
           {:file (.getAbsolutePath (io/file "test-resources/extract_definition.clj"))
            :ns "extract-definition"
            :line line
            ;; silence errors related to tools.analyzer trying to find stuff in refactor-nrepl's own src/ dir, which is mrandersoni-zed.
            ;; it's a pretty extreme edge case so it seems safe to ignore:
            :ignore-errors "true"
            :column col
            :name name
            :dir "test-resources"})
          [:definition :definition]))

(deftest extracts-private-function-definitition-with-docstring-and-meta
  (is (= (-extract-definition "private-function-definition-with-docstring-and-meta" 29 43)
         "(fn
  \"docstring\"
  [foo]
  {:pre [(not (nil? foo))]}
  (do
    (+ 1 2) ; discard this value
    :return))")))

(deftest extracts-private-function-definitition-with-docstring-no-meta
  (is (= (-extract-definition "private-function-definition-with-docstring-no-meta" 30 38)
         "(fn
  \"docstring\"
  [foo]
  (do
    (+ 1 2) ; discard this value
    :value))")))

(deftest extracts-private-function-definitition-without-docstring-and-meta
  (is (= (-extract-definition "private-function-definition-witout-docstring-and-meta" 31 38)
         "(fn
  [foo]
  (do
    (+ 1 2) ; discard this value
    :val))")))

(deftest extracts-private-var-no-docstring
  (is (= (-extract-definition "private-var-no-docstring" 34 3)
         ":value")))

(deftest extracts-private-var-no-docstring-no-value
  (is (= (-extract-definition "private-var-no-docstring-no-value" 35 3)
         "")))

(deftest extracts-private-var-with-docstring
  (is (= (-extract-definition "private-var-with-docstring" 36 3)
         ":value")))

(deftest extracts-var-with-docstringand-value
  (is (= (-extract-definition "var-with-docstring-and-value" 37 3)
         ":value")))

(deftest extracts-let-bound
  (is (= (-extract-definition "let-bound" 42 14)
         "(+ 1 2)")))

(deftest extracts-let-bound-multi-line
  (is (= (-extract-definition "let-bound-multi-line" 42 29)
         "(+ 1 (* 3 2)
                                (- 77 32) ; eol comment
                                (/ 9 3))")))

(deftest extracts-if-let-bound
  (is (= (-extract-definition "if-let-bound" 44 14)
         "(+ 11 17)")))

(deftest returns-meta-data
  (let [res (extract-definition
             {:file (.getAbsolutePath (io/file "test-resources/extract_definition.clj"))
              :ns "extract-definition"
              :line 44
              :column 14
              :name "if-let-bound"
              :dir "."})]
    (is (= (count (:occurrences res)) 1))
    (let [ks (keys (first (:occurrences res)))
          def-ks (keys (:definition res))]
      (is (= (count ks) 7))
      (is (every? #{:line-beg :line-end :col-beg :col-end :name :file :match} ks))

      (is (= (count def-ks) 8))
      (is (every? (conj (set ks) :definition) def-ks)))))

(deftest extracts-public-function
  (is (= (-extract-definition "public-function" 50 12)
         "(fn []
  :value)")))

(deftest extracts-single-char-sym
  (is (= (-extract-definition "a" 52 8)
         "1")))

(deftest handles-muliple-references-in-let
  (is (= (-extract-definition "a" 55 7)
         "1")))

(deftest throws-when-it-cannot-find-definition
  (is (thrown? IllegalStateException
               (-extract-definition "does not exist" 1 1))))
