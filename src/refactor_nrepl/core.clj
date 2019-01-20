(ns refactor-nrepl.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.namespace.parse :as parse]
            [clojure.tools.reader.reader-types :as readers]
            [orchard.classpath]
            [me.raynes.fs :as fs]
            [refactor-nrepl.util :refer [normalize-to-unix-path]]
            [refactor-nrepl.s-expressions :as sexp]
            [refactor-nrepl.config :as config])
  (:import [java.io File FileReader PushbackReader StringReader]))

(defn version []
  (let [v (-> (or (io/resource "refactor-nrepl/refactor-nrepl/project.clj")
                  "project.clj")
              slurp
              read-string
              (nth 2))]
    (assert (string? v)
            (str "Something went wrong, version is not a string: "
                 v))
    v))

(defn ns-from-string
  "Retrieve the symbol naming the ns from file-content."
  [file-content]
  (second (parse/read-ns-decl (PushbackReader. (java.io.StringReader. file-content)))))

(defn ns-name-from-readable
  "Call slurp on readable and extract the ns-name from the content."
  [readable]
  (-> readable slurp ns-from-string))

(defn ignore-dir-on-classpath?
  [^String path]
  (or (.endsWith path "target/srcdeps")
      (reduce (fn [acc x]
                (if (re-find x path)
                  (reduced true)
                  acc))
              false
              (:ignore-paths config/*config*))))

(defn dirs-on-classpath
  "Return all dirs on classpath, filtering out our inlined deps
  directory and paths matching :ignore-paths specified in config.
  Follows the semantics of orchard classpath."
  []
  (->> (orchard.classpath/classpath-directories)
       (remove #(-> % str normalize-to-unix-path (.endsWith "target/srcdeps")))))

(defn project-root
  "Return the project root directory.

  If path-or-file-in-project is provided it should be a project file
  to use as a starting point. We search anything above this dir, until
  we reach the file system root. Default value is the property
  `user.dir`."
  (^File [] (project-root (System/getProperty "user.dir")))
  (^File [path-or-file-in-project]
   (let [path-or-file-in-project (io/file path-or-file-in-project)
         start (if (fs/directory? path-or-file-in-project)
                 path-or-file-in-project
                 (fs/parent path-or-file-in-project))
         names-at-root #{"project.clj" "build.boot" "build.gradle" "pom.xml" "deps.edn"}
         known-root-file? (fn [^File f] (some (fn [known-root-name]
                                                (.endsWith (.getCanonicalPath f)
                                                           known-root-name))
                                              names-at-root))
         root-dir? (fn [^File f] (some known-root-file? (.listFiles f)))
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
                                (memfn ^File getCanonicalPath))
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

(defn read-ns-form
  ([path]
   (with-open [file-reader (FileReader. path)]
     (parse/read-ns-decl (readers/indexing-push-back-reader
                          (PushbackReader. file-reader)))))
  ([dialect path]
   (with-open [file-reader (FileReader. path)]
     (parse/read-ns-decl (readers/indexing-push-back-reader
                          (PushbackReader. file-reader))
                         {:read-cond :allow :features #{dialect}}))))

(defn- data-file?
  "True of f is named like a clj file but represents data.

  E.g. true for data_readers.clj"
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))
        data-files #{"data_readers.clj" "project.clj" "boot.clj"}]
    (reduce (fn [acc data-file] (or acc (.endsWith path data-file)))
            false
            data-files)))

(defn cljc-file?
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))]
    (and (.endsWith path ".cljc")
         (read-ns-form path))))

(defn cljs-file?
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))]
    (and (.endsWith path ".cljs")
         (read-ns-form path))))

(defn clj-file?
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))]
    (and (not (data-file? path-or-file))
         (.endsWith path ".clj")
         (read-ns-form path))))

(defn source-file?
  "True for clj, cljs or cljc files.

  A list of data files are excluded, e.g. data_readers.clj.
  Files without ns form are excluded too."
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
  ^String [symbol-or-string]
  (let [s (-> symbol-or-string
              str
              (str/replace "#'" ""))]
    (if (.endsWith s ".")
      (.substring s 0 (dec (.length s)))
      s)))

(defn prefix
  "java.util.Date -> java.util

  clojure.walk/walk -> clojure.walk
  :clojure.core/kw -> kw"
  [fully-qualified-name]
  (if (re-find #"/" (str fully-qualified-name))
    (when-let [ns-name (-> fully-qualified-name str (.split "/") first)]
      (str/replace ns-name #"^:" ""))
    (let [parts (-> fully-qualified-name str (.split "\\.") butlast)]
      (when (seq parts)
        (str/join "." parts)))))

(defn suffix
  "java.util.Date -> Date
  java.text.Normalizer$Form/NFD => Normalizer
  SomeClass$InnerClass$InnerInnerClass => SomeClass$InnerClass$InnerInnerClass
  SomeClass$InnerClass => SomeClass$InnerClass

  clojure.core/str -> str"
  [fully-qualified-name]
  (let [fully-qualified-name (str fully-qualified-name)]
    (cond
      (= "/" fully-qualified-name)
      fully-qualified-name

      (re-find #"\w\$\w" fully-qualified-name)
      (let [[outer & classes] (-> fully-qualified-name (.split "\\$"))
            outer (suffix outer)]
        (if (or (> (count classes) 1)
                (->> classes first (re-find #"/") not))
          (str/join "$" (apply vector outer classes))
          outer))

      (re-find #"/" (str fully-qualified-name))
      (-> fully-qualified-name str (.split "/") last)

      :else (-> fully-qualified-name str (.split "\\.") last))))

(defn extract-gen-class-methods-meta
  "Retrieve the metadata relative to :methods in the :gen-class top
  level component.

  Returns nil if there is no :gen-class, or if there is no :methods
  inside :gen-class

  .indexOf returns -1 if not found, and since the structure we are
  looking for comes after, by 'incing' by default we can just check
  for zero?"
  [ns-form]
  (let [gen-class (get-ns-component ns-form :gen-class)
        methods_index (inc (if-not (nil? gen-class)
                             (.indexOf gen-class :methods)
                             -1))]
    (if-not (zero? methods_index)
      (apply merge (map #(meta %) (nth gen-class methods_index)))
      nil)))

(defn extract-ns-meta
  "Retrieve the metadata for the ns, if there is any.

  By parsing the ns as a string, and reading the metadata off it, all
  the metadata introduced by the compiler or clojure.test is not
  printed back out"
  [file-content]
  (let [ns-string (sexp/get-first-sexp file-content)
        ns-form (-> ns-string
                    (StringReader.)
                    (PushbackReader.)
                    parse/read-ns-decl)
        ns-meta (meta (second ns-form))]
    {:top-level-meta ns-meta
     :gc-methods-meta (extract-gen-class-methods-meta ns-form)}))

(defn read-ns-form-with-meta
  "Read the ns form found at PATH.

  Dialect is either :clj or :cljs."
  ([path]
   (if-let [ns-form (read-ns-form path)]
     (with-meta ns-form (extract-ns-meta (slurp path)))
     (throw (IllegalStateException. (str "No ns form at " path)))))
  ([dialect path]
   (if-let [ns-form (read-ns-form dialect path)]
     (with-meta ns-form (extract-ns-meta (slurp path)))
     (throw (IllegalStateException. (str "No ns form at " path))))))

(defn path->namespace
  "Read the ns form found at PATH and return the namespace object for
  that ns.

  if NO-ERROR is passed just return nil instead of an exception if we
  can't successfully read an ns form."
  ([path] (path->namespace nil path))
  ([no-error path] (try
                     (some->> path read-ns-form-with-meta second find-ns)
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
     (with-meta (parse/read-ns-decl (PushbackReader. (StringReader. file-content)))
       (extract-ns-meta file-content))
     (catch Exception e
       (throw (IllegalArgumentException. "Malformed ns form!")))))
  ([dialect file-content]
   (let [rdr-opts {:read-cond :allow :features #{dialect}}]
     (try
       (with-meta (parse/read-ns-decl (PushbackReader. (StringReader. file-content)) rdr-opts)
         (extract-ns-meta file-content))
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

(defn normalize-var-name
  [sym-str]
  (str/replace sym-str #"#'.*/" ""))

(defn fully-qualified?
  [symbol-or-keyword]
  (when (prefix symbol-or-keyword)
    symbol-or-keyword))
