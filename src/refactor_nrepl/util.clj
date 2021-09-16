(ns refactor-nrepl.util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (java.io File)
   (java.util.regex Pattern)))

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

(defn inlined-dependency? [candidate]
  (or (-> candidate str (.startsWith "deps."))
      (-> candidate str (.startsWith "mranderson"))
      (-> candidate str (.startsWith "eastwood.copieddeps"))))

(defn- refactor-nrepl? [candidate]
  ;; the refactor-nrepl implmentation should never analyse itself w/ tools.analyzer, as this can cause opaque issues:
  (and (-> candidate str (.startsWith "refactor-nrepl"))
       (not (-> candidate str (.contains "test")))))

(defn self-referential?
  "Does the argument represent namespace name (or a fully qualified name) that should not be analyzed?"
  [candidate]
  ((some-fn inlined-dependency? refactor-nrepl?)
   candidate))

(defn maybe-log-exception [^Throwable e]
  (when (System/getProperty "refactor-nrepl.internal.log-exceptions")
    (.printStackTrace e)))

(defn with-suppressed-errors
  "Wraps a predicate `f` in a try-catch, depending on `ignore-errors?`.

  Typically used for making ingestion of data (`read`/`require`/`analyze`) more robust
  in face of unconfigured or otherwise problematic source paths."
  [f ignore-errors?]
  (if-not ignore-errors?
    f
    (fn [x]
      (try
        (f x)
        (catch Exception e
          (maybe-log-exception e)
          ;; return false, because `with-suppressed-errors` is oriented for predicate usage
          false)))))

(defn interrupted?
  "Has the current thread been interrupted?

  Observing this condition helps `future-cancel` effectively cancel `future`s."
  ([]
   (interrupted? ::_))
  ;; The arity with a "useless" arg is there so that this can be used as a predicate
  ;; in other places that already are using `some-fn`, `every-pred`, etc
  ([_]
   (.isInterrupted (Thread/currentThread))))

(defn dir-outside-root-dir?
  "Dirs outside the root dir often represent uninteresting dirs to examine, e.g.
  Lein checkouts, .gitlibs from deps.edn, or other extraneous source paths
  which aren't directly related to the project.

  By excluding them, one gets better and more accurate performance."
  [^File f]
  {:pre [(-> f .isDirectory)]}
  (let [f (-> f .getCanonicalPath File.)
        root-dir (File. (System/getProperty "user.dir"))
        parent-dirs (->> f
                         (iterate (fn [^File f]
                                    (some-> f .getCanonicalPath File. .getParent File.)))
                         (take-while some?)
                         (set))]
    (not (parent-dirs root-dir))))

(defn data-file?
  "True of f is named like a clj file but represents data.

  E.g. true for data_readers.clj"
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))
        data-files #{"data_readers.clj" "project.clj" "boot.clj"}]
    (reduce (fn [acc data-file]
              (let [v (or acc (.endsWith path data-file))]
                (cond-> v
                  v reduced)))
            false
            data-files)))
