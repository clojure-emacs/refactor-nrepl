(ns refactor-nrepl.test-util
  (:require [refactor-nrepl.analyzer :refer :all]))

(defn test-ast [ns-body]
  (get-ast ns-body))
