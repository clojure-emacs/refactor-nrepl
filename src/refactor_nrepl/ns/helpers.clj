(ns refactor-nrepl.ns.helpers
  (:require [clojure.string :as str]
            [clojure.tools.namespace.parse :refer [read-ns-decl]])
  (:import [java.io FileReader PushbackReader StringReader]))

(defn- libspec?
  [thing]
  (or (vector? thing)
      (symbol? thing)))

(defn prefix-form?
  "Does the form represent a libspec using prefix notation
  like: [prefix libspec1 libspec2 ...] ?"
  [form]
  (and (or (list? form) (vector? form))
       (symbol? (first form))
       (not-any? keyword? form)
       (> (count form) 1)
       (every? libspec? (rest form))))

(defn index-of-component [ns-form type]
  (first (keep-indexed #(when (and (sequential? %2) (= (first %2) type)) %1)
                       ns-form)))

(defn get-ns-component
  "Extracts a sub-component from the ns declaration.

type is either :require, :use or :import"
  [ns type]
  (some->> (index-of-component ns type) (nth ns)))

(defn prefix
  "java.util.Date -> java.util

  clojure.walk/walk -> clojure.walk"
  [fully-qualified-name]
  (if(re-find #"/" (str fully-qualified-name))
    (-> fully-qualified-name str (.split "/") first)
    (let [parts (-> fully-qualified-name str (.split "\\.") butlast)]
      (when (seq parts)
        (str/join "." parts)))))

(defn suffix
  "java.util.Date -> Date
  java.text.Normalizer$Form/NFD => Normalizer

  clojure.core/str -> str"
  [fully-qualified-name]
  (let [fully-qualified-name (str fully-qualified-name)]
    (cond
      (= "/" fully-qualified-name)
      fully-qualified-name

      (re-find #"\$" fully-qualified-name)
      (-> fully-qualified-name (.split "\\$") first suffix)

      (re-find #"/" (str fully-qualified-name))
      (-> fully-qualified-name str (.split "/") last)

      :else (-> fully-qualified-name str (.split "\\.") last))))

(defn ctor-call->str
  "Date. -> \"Date\""
  [sym]
  (let [s (str sym)]
    (if (.endsWith s ".")
      (.substring s 0 (dec (.length s)))
      s)))

(defn read-ns-form
  [path]
  (with-open [file-reader (FileReader. path)]
    (if-let [ns-form (read-ns-decl (PushbackReader. file-reader))]
      ns-form
      (throw (IllegalArgumentException. "Malformed ns form!")))))

(defn file-content-sans-ns [file-content]
  ;; NOTE: It's tempting to trim this result but
  ;; find-macros relies on this not being trimmed
  (let [rdr (PushbackReader. (StringReader. file-content))]
    (read rdr)
    (slurp rdr)))

(defn ns-form-from-string
  [file-content]
  (if-let [ns-form (read-ns-decl (PushbackReader. (StringReader. file-content)))]
    ns-form
    (throw (IllegalArgumentException. "Malformed ns form!"))))

(defn ^String fully-qualify
  "Create a fully qualified name from name and ns."
  [name ns]
  (let [prefix (str ns)
        suffix (suffix name)]
    (when-not (and (seq prefix) (seq suffix))
      (throw (IllegalStateException.
              (str "Can't create a fully qualified symbol from: '" prefix
                   "' and  '" suffix "'"))))
    (str prefix "/" suffix)))
