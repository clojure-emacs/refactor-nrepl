(ns com.example.one
  (:require [com.example.two :as two :refer [foo]]
            [com.example.four :as four]))

(defn bar []
  (str "bar" (two/foo)))

(defn from-registry [k]
  (k four/registry))
