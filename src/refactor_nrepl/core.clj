(ns refactor-nrepl.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.namespace.parse :as parse]
   [clojure.tools.reader :as reader]
   [clojure.tools.reader.reader-types :as readers]
   [orchard.java.classpath :as cp]
   [orchard.misc :as misc]
   [refactor-nrepl.config :as config]
   [refactor-nrepl.s-expressions :as sexp]
   [refactor-nrepl.util :as util :refer [normalize-to-unix-path]])
  (:import
   (clojure.lang LineNumberingPushbackReader)
   (java.io File FileNotFoundException FileReader PushbackReader StringReader)))

;; Require our `fs` customizations before `fs` is loaded:
(require '[refactor-nrepl.fs])
(require '[me.raynes.fs :as fs])

(defn version []
  (let [v (-> "refactor_nrepl/version.edn"
              io/resource
              slurp
              read-string)]
    (assert (string? v)
            (str "Something went wrong, version is not a string: "
                 v))
    v))

(defn ns-from-string
  "Retrieve the symbol naming the ns from file-content."
  [file-content]
  (-> file-content
      java.io.StringReader.
      PushbackReader.
      parse/read-ns-decl
      second))

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
  (->> (cp/classpath)
       (remove misc/archive?)
       (keep #(let [f (io/file %)]
                (when (.isDirectory ^File f) f)))
       (remove (comp ignore-dir-on-classpath? str))))

(def ^:dynamic *skip-resources?*
  "Whether `source-dirs-on-classpath` will skip dirs named 'resources', 'test-resources', etc.

  Should generally always be true, except in a few refactor-nrepl's unit tests."
  true)

(def ^:private resources-dir-pattern
  (re-pattern (str "resources" File/separator "?$")))

(def ^:private target-dir-pattern
  (re-pattern (str "target" File/separator "?$")))

(defn irrelevant-dir?
  "Does `f` look like a directory without files that should be analyzed/refactored?"
  [^File f]
  (let [s (-> f .toString)]
    (boolean (or (if *skip-resources?*
                   (re-find resources-dir-pattern s)
                   false)
                 (re-find target-dir-pattern s)
                 (-> s (.contains ".gitlibs"))))))

(defn source-dirs-on-classpath
  "Like `#'dirs-on-classpath`, but restricted to dirs that look like
  (interesting) source/test dirs."
  []
  (->> (dirs-on-classpath)
       (remove irrelevant-dir?)
       (remove util/dir-outside-root-dir?)))

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
         names-at-root #{"project.clj" "build.boot" "build.gradle" "pom.xml" "deps.edn" "shadow-cljs.edn"}
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

  Note that files which are non-existent, hidden or build-artifacts
  are pruned by this function."
  [pred dir]
  ;; ensure pmap is not used lazily:
  {:post [(vector? %)]}
  (->> dir
       file-seq
       ;; `pmap` performs better in large projects.
       (pmap (fn [f]
               (when ((every-pred fs/exists?
                                  (complement fs/hidden?)
                                  pred
                                  (complement build-artifact?))
                      f)
                 f)))
       (filterv identity)))

(defn read-ns-form
  ([path]
   (read-ns-form nil path))
  ([dialect path]
   (let [^String path-string (when (string? path)
                               path)
         ^File path-file (when-not path-string
                           path)
         ^File file (or path-file (File. path-string))]
     (try
       (with-open [file-reader (FileReader. file)]
         (try
           (parse/read-ns-decl (readers/indexing-push-back-reader
                                (PushbackReader. file-reader))
                               (if dialect
                                 {:read-cond :allow :features #{dialect}}
                                 nil))
           (catch Exception _ nil)))
       (catch FileNotFoundException e
         (throw (ex-info (format "No such file: %s. This typically indicates an invalid request client-side."
                                 (pr-str path))
                         {:path path
                          :dialect dialect
                          :file (str file)}
                         e)))))))

(defn cljc-extension? [^String path]
  (.endsWith path ".cljc"))

(defn cljc-file?
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))]
    (boolean (and (cljc-extension? path)
                  (read-ns-form path)))))

(defn cljs-extension? [^String path]
  (.endsWith path ".cljs"))

(defn cljs-file?
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))]
    (boolean (and (cljs-extension? path)
                  (read-ns-form path)))))

(defn clj-extension? [^String path]
  (.endsWith path ".clj"))

(defn clj-file?
  [path-or-file]
  (let [path (.getPath (io/file path-or-file))]
    (boolean (and (not (util/data-file? path-or-file))
                  (clj-extension? path)
                  (read-ns-form path)))))

(defn clj-or-cljc-file?
  [path-or-file]
  (or (clj-file? path-or-file)
      (cljc-file? path-or-file)))

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
  ([pred]
   (find-in-project pred (dirs-on-classpath)))
  ([pred dirs]
   (->> dirs
        (pmap (partial find-in-dir pred))
        (apply concat)
        distinct)))

(defn source-files-with-clj-like-extension
  "Finds files with .clj* extension in the project, without inspecting them.

  Meant as a particularly fast operation (as it doesn't slurp files)."
  ([ignore-errors?]
   (source-files-with-clj-like-extension ignore-errors? (source-dirs-on-classpath)))
  ([ignore-errors? dirs]
   (find-in-project (util/with-suppressed-errors
                      (comp (some-fn clj-extension?
                                     cljc-extension?
                                     cljs-extension?)
                            (fn [^File f]
                              (.getPath f)))
                      ignore-errors?)
                    dirs)))

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
  (let [^clojure.lang.PersistentVector gen-class (some-> ns-form
                                                         (get-ns-component :gen-class)
                                                         vec)
        methods_index (if-not (nil? gen-class)
                        (-> gen-class (.indexOf :methods) inc)
                        0)]
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
        ns-meta (meta (second ns-form))
        attr-map (->> ns-form
                      (drop 2)
                      (take 2)
                      (some (fn [e] (when (map? e) e))))]
    {:top-level-meta ns-meta
     :gc-methods-meta (extract-gen-class-methods-meta ns-form)
     :attr-map attr-map}))

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

(def require-lock (Object.))

(defn safe-find-ns [n ignore-error?]
  (try
    (locking require-lock
      (require n))
    (find-ns n)
    (catch Throwable e
      (util/maybe-log-exception e)
      (when-not ignore-error?
        (throw e)))))

(defn path->namespace
  "Read the ns form found at PATH and return the namespace object for
  that ns.

  if NO-ERROR is passed just return nil instead of an exception if we
  can't successfully read an ns form."
  ([path] (path->namespace nil path))
  ([no-error path] (when-not (cljs-file? path)
                     (some-> path read-ns-form-with-meta parse/name-from-ns-decl (safe-find-ns no-error)))))

(defn file-forms
  "For a given `file`, get all the forms from it.

  If `file` is .cljc, `features` (a set) will be used.

  Please prefer this helper over `#'slurp`,
  so that reader conditionals are properly handled."
  [file features]
  (let [reader (LineNumberingPushbackReader. (StringReader. (slurp file)))
        reader-opts {:read-cond :allow
                     :eof ::eof
                     :features (case (file->dialect file)
                                 :clj #{:clj}
                                 :cljc features
                                 :cljs #{:cljs})}]
    (loop [forms []
           form (reader/read reader-opts reader)]
      (if (not= form ::eof)
        (recur (conj forms form)
               (reader/read reader-opts reader))
        (str/join " " forms)))))

(defn file-content-sans-ns
  "Read the content of file after the ns.

  Any whitespace in the file is preserved, including the conventional
  blank line after the ns form, before the rest of the file content.

  The default value of dialect is :clj."
  ([file-content] (file-content-sans-ns file-content :clj))
  ([file-content dialect]
   ;; NOTE: It's tempting to trim this result but
   ;; find-macros relies on this not being trimmed
   (let [reader-opts {:read-cond :allow :features #{dialect}}
         reader (PushbackReader. (StringReader. file-content))]
     (read reader-opts reader)
     (slurp reader))))

(defn ns-form-from-string
  ([file-content]
   (try
     (with-meta (parse/read-ns-decl (PushbackReader. (StringReader. file-content)))
       (extract-ns-meta file-content))
     (catch Exception _e
       (throw (IllegalArgumentException. "Malformed ns form!")))))
  ([dialect file-content]
   (let [reader-opts {:read-cond :allow :features #{dialect}}]
     (try
       (with-meta (parse/read-ns-decl (PushbackReader. (StringReader. file-content)) reader-opts)
         (extract-ns-meta file-content))
       (catch Exception _e
         (throw (IllegalArgumentException. "Malformed ns form!")))))))

(defn fully-qualify
  "Create a fully qualified name from name and ns."
  ^String
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

(defmacro with-clojure-version->=
  "Guard the evaluation of `body` with a test on the current clojure version."
  {:style/indent 1}
  [{:keys [major minor] :as _clojure-version} & body]
  (let [major (long major)
        minor (long minor)]
    (when (or (> (-> *clojure-version* :major long) major)
              (and (= (-> *clojure-version* :major long) major)
                   (>= (-> *clojure-version* :minor long) minor)))
      `(do ~@body))))
