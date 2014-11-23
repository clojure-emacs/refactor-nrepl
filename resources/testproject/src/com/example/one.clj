(ns com.example.one
  (:require [com.example.two :as two :refer [foo]]))

(defn bar []
  (str "bar" (two/foo)))
