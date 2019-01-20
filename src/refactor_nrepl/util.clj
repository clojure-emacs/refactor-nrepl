(ns refactor-nrepl.util
  (:require [clojure.string :as string])
  (:import java.util.regex.Pattern))

(defn normalize-to-unix-path
  "Replace use / as separator and lower-case."
  ^String [^String path]
  (if (.contains (System/getProperty "os.name") "Windows")
    (.replaceAll path (Pattern/quote "\\") "/")
    path))

(defn filter-map
  "Return a new map where (pred [k v]) is true for every key-value pair."
  [pred m]
  (into {} (filter pred m)))

(defn dissoc-when
  "Remove the enumerated keys from m on which pred is truthy."
  [m pred & ks]
  (if (seq ks)
    (reduce (fn [m k] (if (pred (get m k)) (dissoc m k) m)) m ks)
    m))

(defn ex-info-assoc
  "Assoc kvs onto e's data map."
  [^clojure.lang.ExceptionInfo e & kvs]
  (ex-info (.getMessage e) (apply assoc (ex-data e) kvs) (.getCause e)))

(defmacro with-additional-ex-data
  "Execute body and if an ex-info is thrown, assoc kvs onto the data
  map and rethrow."
  [kvs & body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (throw (apply ex-info-assoc e# ~kvs)))))

(defn conj-some
  "Like conj but nil values are discared from xs."
  [coll & xs]
  (let [xs (remove nil? xs)]
    (if (seq xs)
      (apply conj coll xs)
      coll)))

(defn replace-last
  "Replaces the last instance of match from the string s with rep"
  [s match rep]
  (as-> s _
    (reverse _)
    (apply str _)
    (string/replace-first _ match rep)
    (reverse _)
    (apply str _)))
