(ns com.example.five
  (:require [clojure.string :refer [join split blank? trim] :as str]))

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

(defn find-in-let [s p]
  (let [z (trim p)]
    (assoc {:s s
            :p p
            :z z} :k "foobar")))

(defn threading-macro [strings]
  (let [sep ","]
    (->> strings
         flatten
         (join sep))))

(defn repeated-sexp []
  (map name [:a :b :c])
  (let [name #(str "myname" %)]
    (map name [:a :b :c])))

(defn sexp-with-anon-fn [n]
  (let [g 5]
    (#(+ g %) n)))

(defn many-params [x y z a b c]
  (* x y z a b c))
