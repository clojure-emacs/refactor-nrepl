(ns refactor-nrepl.util
  (:require [clojure.java.classpath :as cp]
            [clojure.java.io :as io]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [clojure.tools.namespace
             [find :as find]
             [parse :as parse]]
            [clojure.walk :as walk]
            [me.raynes.fs :as fs]
            [rewrite-clj.zip :as zip])
  (:import [java.io File PushbackReader]
           java.util.regex.Pattern))

(defn ns-from-string
  "Retrieve the symbol naming the ns from file-content."
  [file-content]
  (second (parse/read-ns-decl (PushbackReader. (java.io.StringReader. file-content)))))

(defn normalize-to-unix-path
  "Replace use / as separator and lower-case."
  [path]
  (if (.contains (System/getProperty "os.name") "Windows")
    (.replaceAll path (Pattern/quote "\\") "/")
    path))

(defn dirs-on-classpath
  "Return all dirs on classpath, filtering out our inlined deps
  directory."
  []
  (->> (cp/classpath)
       (filter fs/directory?)
       (remove #(-> % str (.endsWith "target/srcdeps")))))

(defn find-clojure-sources-in-project
  "Return all clojure files in the project that are on the classpath."
  []
  (mapcat find/find-sources-in-dir (dirs-on-classpath)))

(defn find-in-dir
  "Searches recursively under dir for files matching (pred ^File file). "
  [pred ^File dir]
  (filter pred (file-seq dir)))

(defn cljc-file?
  [path-or-file]
  (.endsWith (.getPath (io/file path-or-file)) ".cljc"))

(defn cljs-file?
  [path-or-file]
  (.endsWith (.getPath (io/file path-or-file)) ".cljs"))

(defn clj-file?
  [path-or-file]
  (.endsWith (.getPath (io/file path-or-file)) ".clj"))

(defn source-file?
  "True for clj, cljs or cljc files."
  [path-or-file]
  ((some-fn cljc-file? cljs-file? clj-file?) (io/file path-or-file)))


(defn file->dialect
  "Return the clojure dialect used in the file f.

  The dialect is either :clj, :cljs or :cljc."
  [path-or-file]
  (let [f (io/file path-or-file)]
    (cond
      (clj-file? f) :clj
      (cljs-file? f) :cljs
      (cljc-file? f) :cljc
      :else (throw (ex-info "Path isn't pointing to file in a clj dialect!"
                            {:path path-or-file})))))

(defn filter-project-files
  "Return the files in the project satisfying (pred ^File file)."
  [pred]
  (mapcat (partial find-in-dir pred) (dirs-on-classpath)))

(defn throw-unless-clj-file [file-path]
  (when-not (re-matches #".+\.clj$" file-path)
    (throw (IllegalArgumentException.
            "Only .clj files are supported!"))))

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
