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

type is a toplevel keyword in the ns form e.g. :require or :use."
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
  "Read the ns form found at PATH.

  Dialect is either :clj or :cljs."
  ([path]
   (with-open [file-reader (FileReader. path)]
     (if-let [ns-form (read-ns-decl (PushbackReader. file-reader))]
       ns-form
       (throw (IllegalStateException. (str "No ns form at " path))))))
  ([dialect path]
   (with-open [file-reader (FileReader. path)]
     (if-let [ns-form (read-ns-decl (PushbackReader. file-reader)
                                    {:read-cond :allow :features #{dialect}})]
       ns-form
       (throw (IllegalStateException. (str "No ns form at " path)))))))

(defn path->namespace
  "Read the ns form found at PATH and return the namespace object for
  that ns.

  if NO-ERROR is passed just return nil instead of an exception if we
  can't successfully read an ns form."
  ([path] (path->namespace nil path))
  ([no-error path] (try
                     (some->> path (read-ns-form) second find-ns)
                     (catch Exception e
                       (when-not no-error
                         (throw e))))))

(defn file-content-sans-ns
  "Read the content of file after the ns.

  Any whitespace in the file is preserved, including the conventional
  blank line after the ns form, before the rest of the file content.

  The default value of dialect is :clj."
  ([file-content] (file-content-sans-ns file-content :clj))
  ([file-content dialect]
   ;; NOTE: It's tempting to trim this result but
   ;; find-macros relies on this not being trimmed
   (let [rdr-opts {:read-cond :allow :features #{dialect}}
         rdr (PushbackReader. (StringReader. file-content))]
     (read rdr-opts rdr)
     (slurp rdr))))

(defn ns-form-from-string
  ([file-content]
   (try
     (read-ns-decl (PushbackReader. (StringReader. file-content)))
     (catch Exception e
       (throw (IllegalArgumentException. "Malformed ns form!")))))
  ([dialect file-content]
   (let [rdr-opts {:read-cond :allow :features #{dialect}}]
     (try
       (read-ns-decl (PushbackReader. (StringReader. file-content)) rdr-opts)
       (catch Exception e
         (throw (IllegalArgumentException. "Malformed ns form!")))))))

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
