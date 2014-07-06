(ns refactor-nrepl.test-util
  (:require [refactor-nrepl.analyzer :refer :all]))

(defn test-ast [ns-body]
  (string-ast ns-body))
