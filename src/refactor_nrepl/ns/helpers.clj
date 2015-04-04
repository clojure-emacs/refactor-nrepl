(ns refactor-nrepl.ns.helpers
  (:require [clojure.string :as str]
            [clojure.tools.namespace.parse :refer [read-ns-decl]])
  (:import java.io.PushbackReader
           java.io.FileReader))

(defn- libspec?
  [thing]
  (or (vector? thing)
      (symbol? thing)))

(defn prefix-form?
  "True if the vector is of the form [prefix libspec1 libspec2...]"
  [v]
  (and (vector? v)
       (symbol? (first v))
       (not-any? keyword? v)
       (> (count v) 1)
       (every? libspec? (rest v))))

(defn index-of-component [ns-form type]
  (first (keep-indexed #(when (and (sequential? %2) (= (first %2) type)) %1)
                       ns-form)))

(defn get-ns-component
  "Extracts a sub-component from the ns declaration.

type is either :require, :use or :import"
  [ns type]
  (some->> (index-of-component ns type) (nth ns)))

(defn prefix
  "java.util.Date -> java.util"
  [fully-qualified-name]
  (let [parts (-> fully-qualified-name str (.split "\\.") butlast)]
    (when (seq parts)
      (str/join "." parts))))

(defn suffix
  "java.util.Date -> Date
  clojure.core/str -> str"
  [fully-qulified-name]
  (if (re-find #"/" (str fully-qulified-name))
    (-> fully-qulified-name str (.split "/") last)
    (-> fully-qulified-name str (.split "\\.") last)))

(defn read-ns-form
  [path]
  (if-let [ns-form
           (read-ns-decl (PushbackReader. (FileReader. path)))]
    ns-form
    (throw (IllegalArgumentException. "Malformed ns form!"))))

(defn file-content-sans-ns [file-content]
  (let [rdr (PushbackReader. (StringReader. file-content))]
    (read rdr)
    (str/triml (slurp rdr))))
