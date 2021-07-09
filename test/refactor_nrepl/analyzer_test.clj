(ns refactor-nrepl.analyzer-test
  (:require
   [clojure.java.io :as io]
   [refactor-nrepl.analyzer :as sut]
   [clojure.test :refer [deftest is]]))

(deftest ns-ast-test
  (doseq [f ["core_async_usage.clj"]
          :let [c (-> f io/resource slurp)]]
    (is (some? (sut/ns-ast c)))))
