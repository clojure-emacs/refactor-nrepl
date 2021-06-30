(ns refactor-nrepl.find.find-used-publics-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [refactor-nrepl.find.find-used-publics :as fup]))

(defn- absolute-path [^String path]
  (.getAbsolutePath (io/file path)))

(def ns2-path (absolute-path "test-resources/ns2.clj"))
(def macro-usage (absolute-path "testproject/src/com/example/referred_macro_usage.clj"))
(def protocol-usage (absolute-path "testproject/src/com/example/protocol_usage.clj"))

(deftest finds-symbols-of-ns
  (is (= '("get-pretty-writer" "fresh-line" "cl-format")
         (->> (fup/find-used-publics {:file ns2-path :used-ns "clojure.pprint"})
              (map :name)))))

(deftest finds-macros-of-ns
  (is (= '("my-macro")
         (->> (fup/find-used-publics {:file macro-usage :used-ns "com.example.macro-def"})
              (map :name)))))

(deftest find-protocols-of-ns
  (is (= '("MyProtocol" "MyProtocol")
         (->> (fup/find-used-publics {:file protocol-usage :used-ns "com.example.protocol-def"})
              (map :name)))))
