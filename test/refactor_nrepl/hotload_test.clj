(ns refactor-nrepl.hotload-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [refactor-nrepl.artifacts :as artifacts]
   [refactor-nrepl.hotload :as hotload]))

(def parse-coordinates @#'artifacts/parse-coordinates)

(deftest parse-coordinates-test
  (testing "Leiningen-style vector"
    (is (= '{hiccup {:mvn/version "2.0.0"}}
           (parse-coordinates "[hiccup \"2.0.0\"]"))))

  (testing "qualified lib name"
    (is (= '{org.clojure/core.async {:mvn/version "1.6.681"}}
           (parse-coordinates "[org.clojure/core.async \"1.6.681\"]"))))

  (testing "deps.edn-style map literal"
    (is (= '{hiccup {:mvn/version "2.0.0"}}
           (parse-coordinates "{hiccup {:mvn/version \"2.0.0\"}}"))))

  (testing "invalid input"
    (is (thrown? IllegalArgumentException
                 (parse-coordinates "\"hiccup\"")))))

(deftest add-libs!-smoke-test
  (testing "Adds a small library end-to-end and subsequent calls are no-ops."
    (let [added (hotload/add-libs! '{hiccup {:mvn/version "2.0.0-RC3"}})]
      (is (contains? added 'hiccup))
      (is (seq (:paths (get added 'hiccup))))
      (require 'hiccup.core)
      (is (some? (resolve 'hiccup.core/html))))

    (testing "re-adding is a no-op"
      (is (empty? (hotload/add-libs! '{hiccup {:mvn/version "2.0.0-RC3"}}))))))
