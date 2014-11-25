(ns refactor-nrepl.ns.helpers
  (:require [clojure.string :as str]))

(defn- libspec?
  [thing]
  (or (vector? thing)
      (symbol? thing)))

(defn prefix-form?
  "True if the vector is of the form [prefix libspec1 libspec2...]"
  [v]
  (and (sequential? v)
       (symbol? (first v))
       (every? libspec? (rest v))))

(defn index-of-component [ns-form type]
  (->> (keep-indexed #(when (and (sequential? %2) (= (first %2) type)) %1)
                     ns-form)
       first))

(defn get-ns-component
  "Extracts a sub-component from the ns declaration.

type is either :require, :use or :import"
  [ns type]
  (some->> (index-of-component ns type) (nth ns)))

(defn prefix
  "java.util.Date -> java.util"
  [fully-qualified-name]
  (str/join "." (-> fully-qualified-name str (.split "\\.") butlast)))

(defn suffix
  "java.util.Date -> Date"
  [fully-qulified-name]
  (-> fully-qulified-name str (.split "\\.") last))
