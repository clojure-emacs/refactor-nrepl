(ns com.example.shadowed-macro-usages
  (:require [com.example.macro-def :refer [my-macro]]))

(defn shadow-macro-in-fn-param [my-macro]
  (my-macro :shadowed-by-function-param))

(defn shadow-macro-in-let [my-macro]
  (let [my-macro (fn not-a-macro [])]
    (my-macro :shadowed-by-let)))
