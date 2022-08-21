(ns refactor-nrepl.analyzer-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.analyzer :as sut]))

(deftest ns-ast-test
  (doseq [f ["core_async_usage.clj"
             "clashing_defprotocol_method_name.clj"]
          :let [c (-> f io/resource slurp)]]
    (is (some? (sut/ns-ast c)))))
