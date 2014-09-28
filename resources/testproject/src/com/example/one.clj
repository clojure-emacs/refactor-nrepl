(ns com.example.one
  (:require [com.example.two :as two]))

(defn bar []
  (str "bar" (two/foo)))
