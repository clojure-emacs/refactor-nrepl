(ns testproject.src.com.example.five
  (:require [clojure.string :refer [join split blank? trim]]))

;;  remove parameters to run the tests
(defn fn-with-unbounds [s sep]
  (when-not (blank? s)
    (-> s (split #" ")
        (join sep)
        trim)))

(defn orig-fn [s]
  (let [sep ";"]
    (when-not (blank? s)
      (-> s
          (split #" ")
          ((partial join sep))
          trim))))
