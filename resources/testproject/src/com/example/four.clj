(ns com.example.four
  (:require [clojure.string :refer [split join]]))

(defn some-fn-with-split []
  (split "foo:bar:baz" #":"))
