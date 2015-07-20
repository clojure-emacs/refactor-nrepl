(ns com.example.aliased-macro-usage
  (:require [com.example.macro-def :as m]))

(defn referred-macro-usage []
  (m/my-macro :aliased))
