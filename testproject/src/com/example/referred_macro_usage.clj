(ns com.example.referred-macro-usage
  (:require [com.example.macro-def :refer [my-macro]]))

(defn referred-macro-usage []
  (my-macro :referred))
