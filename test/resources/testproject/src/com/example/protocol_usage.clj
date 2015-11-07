(ns com.example.protocol-usage
  (:require [com.example.protocol-def :refer :all]))

(defrecord MyRecord []
  MyProtocol
  (my-fn [arg]
    (println arg)))

(deftype MyType []
  MyProtocol
  (my-fn [arg]
    (println arg)))
