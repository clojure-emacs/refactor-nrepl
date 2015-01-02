(ns com.example.four
  (:require [clojure.string :refer [split join]]
            [com.example.three :refer [fn-with-println thre]]))

(defn some-fn-with-split []
  (split "foo:bar:baz" #":"))

(def registry
  {:fn-with-println #'fn-with-println})

(thre)
