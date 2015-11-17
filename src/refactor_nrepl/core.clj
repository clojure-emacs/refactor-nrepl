(ns refactor-nrepl.core
  (:require [clojure.java.classpath :as cp]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.namespace.parse :as parse]
            [clojure.tools.reader.reader-types :as readers]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [refactor-nrepl.util :refer [normalize-to-unix-path]])
  (:import [java.io File FileReader PushbackReader StringReader]))

(defn ns-from-string
  "Retrieve the symbol naming the ns from file-content."
  [file-content]
  (second (parse/read-ns-decl (PushbackReader. (java.io.StringReader. file-content)))))

(defn ns-name-from-readable
  "Call slurp on readable and extract the ns-name from the content."
  [readable]
  (-> readable slurp ns-from-string))

(defn dirs-on-classpath
  "Return all dirs on classpath, filtering out our inlined deps
  directory."
  []
  (->> (cp/classpath)
       (filter fs/directory?)
       (remove #(-> % str normalize-to-unix-path (.endsWith "target/srcdeps")))))

(defn project-root
  "Return the project root directory.

  If path-or-file-in-project is provided it should be a project file
  to use as a starting point. We search anything above this dir, until
  we reach the file system root. Default value is the property
  'user.dir'."
  ([] (project-root (System/getProperty "user.dir")))
  ([path-or-file-in-project]
   (let [path-or-file-in-project (io/file path-or-file-in-project)
         start (if (fs/directory? path-or-file-in-project)
                 path-or-file-in-project
                 (fs/parent path-or-file-in-project))
         names-at-root #{"project.clj" "build.boot" "build.gradle" "pom.xml"}
         known-root-file? (fn [f] (some (fn [known-root-name]
                                          (.endsWith (.getCanonicalPath f)
                                                     known-root-name))
                                        names-at-root))
         root-dir? (fn [f] (some known-root-file? (.listFiles f)))
         most-likely-root (io/file (System/getProperty "user.dir"))]
     (if (root-dir? most-likely-root)
       most-likely-root
       (loop [f start]
         (cond
           (root-dir? f) f
           (nil? (fs/parent f)) nil
           :else (recur (fs/parent f))))))))

(defn build-artifact? [path-or-file]
  (let [f (io/file path-or-file)
        target-path (-> path-or-file project-root .getCanonicalPath
                        normalize-to-unix-path
                        (str "/target"))
        parent-paths (map (comp normalize-to-unix-path
                                (memfn getCanonicalPath))
                          (fs/parents f))]
    (and (some #{target-path} parent-paths)
         path-or-file)))

(defn find-in-dir
  "Searches recursively under dir for files matching (pred ^File file).

  Note that files which are non-existant, hidden or build-artifacts
  are pruned by this function."
  [pred dir]
  (->>  dir
        io/file
        file-seq
        (filter (every-pred fs/exists?
                            (complement fs/hidden?)
                            pred
                            (complement build-artifact?)))))

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

(defn find-in-project
  "Return the files in the project satisfying (pred ^File file)."
  [pred]
  (-> find-in-dir (partial pred) (mapcat (dirs-on-classpath)) distinct))

(defn throw-unless-clj-file [file-path]
  (when-not (re-matches #".+\.clj$" file-path)
    (throw (IllegalArgumentException.
            "Only .clj files are supported!"))))

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

(defn strip-reader-macros
  "Strip reader macros like #' and . (as in '(Date.)') from
  symbol-or-string."
  [symbol-or-string]
  (let [s (-> symbol-or-string
              str
              (str/replace "#'" ""))]
    (if (.endsWith s ".")
      (.substring s 0 (dec (.length s)))
      s)))

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

(defn read-ns-form
  "Read the ns form found at PATH.

  Dialect is either :clj or :cljs."
  ([path]
   (with-open [file-reader (FileReader. path)]
     (if-let [ns-form (parse/read-ns-decl (readers/indexing-push-back-reader
                                           (PushbackReader. file-reader)))]
       ns-form
       (throw (IllegalStateException. (str "No ns form at " path))))))
  ([dialect path]
   (with-open [file-reader (FileReader. path)]
     (if-let [ns-form (parse/read-ns-decl (readers/indexing-push-back-reader
                                           (PushbackReader. file-reader))
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
                     (some->> path read-ns-form second find-ns)
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
     (parse/read-ns-decl (PushbackReader. (StringReader. file-content)))
     (catch Exception e
       (throw (IllegalArgumentException. "Malformed ns form!")))))
  ([dialect file-content]
   (let [rdr-opts {:read-cond :allow :features #{dialect}}]
     (try
       (parse/read-ns-decl (PushbackReader. (StringReader. file-content)) rdr-opts)
       (catch Exception e
         (throw (IllegalArgumentException. "Malformed ns form!")))))))

(defn ^String fully-qualify
  "Create a fully qualified name from name and ns."
  [ns name]
  (let [prefix (str ns)
        suffix (suffix name)]
    (when-not (and (seq prefix) (seq suffix))
      (throw (IllegalStateException.
              (str "Can't create a fully qualified symbol from: '" prefix
                   "' and  '" suffix "'"))))
    (str prefix "/" suffix)))
