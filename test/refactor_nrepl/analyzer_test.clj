(ns refactor-nrepl.analyzer-test
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.analyzer :as sut]))

(deftest ns-ast-test
  (doseq [f ["core_async_usage.clj"
             "clashing_defprotocol_method_name.clj"]
          :let [c (-> f io/resource slurp)]]
    (is (some? (sut/ns-ast c)))))

(deftest warm-ast-cache-test
  (let [pairs (partition 2 (sut/warm-ast-cache))]
    (when (System/getenv "CI")
      (pprint/pprint pairs))
    (doseq [[ns-sym result] pairs]
      (is (simple-symbol? ns-sym))
      (is (or (= "OK" result)
              (and (list? result)
                   (-> result first #{"error"})
                   (-> result second string?)))))))
