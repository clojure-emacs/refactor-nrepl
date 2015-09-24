(ns refactor-nrepl.rename-file-or-dir
  (:require [clojure.string :as str]
            [clojure.tools.namespace.file :as file]
            [me.raynes.fs :as fs]
            [refactor-nrepl.core :as core]
            [refactor-nrepl
             [config :as config]
             [util :as util]]
            [refactor-nrepl.ns
             [ns-parser :as ns-parser]
             [pprint :refer [pprint-ns]]
             [rebuild :refer [rebuild-ns-form]]
             [tracker :as tracker]])
  (:import java.io.File
           java.nio.file.Files
           java.util.regex.Pattern))

(declare -rename-file-or-dir)

(defn- chop-src-dir-prefix
  "Given a path cuts away the part matching a dir on classpath.

  We use this as a crude way to find the source directories on the classpath."
  [path]
  (let [chop-prefix (fn [dir]
                      (->> dir
                           str/lower-case
                           Pattern/quote
                           re-pattern
                           (str/split (str/lower-case path))
                           second))
        shortest (fn [acc val] (if (< (.length acc) (.length val)) acc val))]
    (let [relative-paths (->> (util/dirs-on-classpath) (map chop-prefix) (remove nil?))]
      (if-let [p (cond
                   (= (count relative-paths) 1) (first relative-paths)
                   (> (count relative-paths) 1) (reduce shortest relative-paths))]
        (if (.startsWith p "/")
          (.substring p 1)
          p)
        (throw (IllegalStateException. (str "Can't find src dir prefix for path " path)))))))

;; Taken from raynes' fs and modified to work with cljc and cljs
(defn path-ns
  "Takes a `path` to a source file and constructs a namespace symbol
   out of the path."
  [path]
  (-> path
      (str/replace #"\.clj[cs]?" "")
      (str/replace \_ \-)
      (str/replace \/ \.)
      symbol))

(defn- path->ns
  "Given an absolute file path to a non-existing file determine the
  name of the ns."
  [new-path]
  (-> new-path util/normalize-to-unix-path chop-src-dir-prefix path-ns))

(defn update-ns-reference-in-libspec
  [old-ns new-ns libspec]
  (if (= (:ns libspec) old-ns)
    (assoc libspec :ns new-ns)
    libspec))

(defn- update-libspecs
  "Replaces any references old-ns with new-ns in all libspecs."
  [libspecs old-ns new-ns]
  (map (partial update-ns-reference-in-libspec old-ns new-ns) libspecs))

(defn- replace-package-prefix
  [old-prefix new-prefix class]
  (if (.startsWith (str class) old-prefix)
    (str/replace (str class) old-prefix new-prefix)
    class))

(defn- update-class-references
  [classes old-ns new-ns]
  (let [old-prefix (str/replace (str old-ns) "-" "_")
        new-prefix (str/replace (str new-ns) "-" "_")]
    (map (partial replace-package-prefix old-prefix new-prefix) classes)))

(defn- update-references-in-deps
  [parsed-ns old-ns new-ns]
  (let [dialect (:source-dialect parsed-ns)
        libspecs (-> parsed-ns dialect :require)
        require-macros (-> parsed-ns :cljs :require-macros)
        classes (->  parsed-ns dialect :import)]
    (merge parsed-ns
           {dialect
            (merge {:require (update-libspecs libspecs old-ns new-ns)
                    :import (update-class-references classes old-ns new-ns)}
                   (when (= dialect :cljs)
                     {:require-macros (update-libspecs require-macros old-ns new-ns)}))})))

(defn- create-new-ns-form
  "Reads file and returns an updated ns."
  [file old-ns new-ns]
  (let [ns-form (core/read-ns-form file)
        parsed-ns (ns-parser/parse-ns file)
        deps (update-references-in-deps parsed-ns old-ns new-ns)]
    (pprint-ns (rebuild-ns-form deps ns-form) (.getAbsolutePath file))))

(defn- update-file-content-sans-ns
  "Any fully qualified references to old-ns has to be replaced with new-ns."
  [file old-ns new-ns]
  (let [old-prefix (str (str/replace old-ns "-" "_") "/")
        new-prefix (str (str/replace new-ns "-" "_") "/")
        old-ns-ref (str old-ns "/")
        new-ns-ref (str new-ns "/")]
    (-> file
        slurp
        core/file-content-sans-ns
        str/triml
        (str/replace old-prefix new-prefix)
        (str/replace old-ns-ref new-ns-ref))))

(defn- update-dependent
  "New content for a dependent file."
  [file old-ns new-ns]
  (str (create-new-ns-form file old-ns new-ns)
       "\n"
       (update-file-content-sans-ns file old-ns new-ns)))

(defn- rename-file!
  "Actually rename a file."
  [old-path new-path]
  (fs/mkdirs (fs/parent new-path))
  (fs/rename old-path new-path)
  (loop [dir (.getParentFile (File. old-path))]
    (when (empty? (.listFiles dir))
      (.delete dir)
      (recur (.getParentFile dir)))))

(defn- update-dependents!
  "Actually write new content for dependents"
  [new-dependents]
  (doseq [[f content] new-dependents]
    (spit f content)))

(defn- update-ns!
  "After moving some file to path update its ns to reflect new location."
  [path old-ns]
  (let [new-ns (path->ns path)
        f (File. path)]
    (->> new-ns
         str
         (str/replace-first (slurp f) (str old-ns))
         (spit f))))

(defn- moving-dirs? [old-path new-path]
  (let [p1 (util/normalize-to-unix-path old-path)
        p2 (util/normalize-to-unix-path new-path)]
    (not= (butlast (str/split p1 #"/"))
          (butlast (str/split p2 #"/")))))

(defn- calculate-affected-files
  [old-path new-path dependents]
  ;; When the directory is changed we don't yet know what the path of
  ;; the new dependents will be
  (if (moving-dirs? old-path new-path)
    [new-path]
    (conj dependents new-path)))

(defn- rename-source-file
  "Move file from old to new, updating any dependents."
  [old-path new-path]
  (let [old-ns (util/ns-from-string (slurp old-path))
        new-ns (path->ns new-path)
        tracker (tracker/build-tracker)
        dependents (tracker/get-dependents tracker old-ns)
        new-dependents (atom {})]
    (doseq [f dependents]
      (swap! new-dependents
             assoc (.getAbsolutePath f) (update-dependent f old-ns new-ns)))
    (rename-file! old-path new-path)
    (update-ns! new-path old-ns)
    (update-dependents! @new-dependents)
    (calculate-affected-files old-path new-path (keys @new-dependents))))

(defn- merge-paths
  "Update path with new prefix when parent dir is moved"
  [path old-parent new-parent]
  (str/replace path old-parent new-parent))

(defn- rename-dir [old-path new-path]
  (let [old-path (util/normalize-to-unix-path old-path)
        new-path (util/normalize-to-unix-path new-path)
        old-path (if (.endsWith old-path "/") old-path (str old-path "/"))
        new-path (if (.endsWith new-path "/") new-path (str new-path "/"))]
    (flatten (for [f (file-seq (File. old-path))
                   :when (not (fs/directory? f))
                   :let [path (util/normalize-to-unix-path (.getAbsolutePath f))]]
               (-rename-file-or-dir path (merge-paths path old-path new-path))))))

(defn- file-or-symlink-exists? [path]
  (let [f (File. path)]
    (if (.exists f)
      path
      (let [p (.toPath f)]
        (when (Files/isSymbolicLink p)
          (let [target (Files/readSymbolicLink p)]
            (when (.. target toFile exists)
              path)))))))

(defn- -rename-file-or-dir [old-path new-path]
  (let [affected-files  (if (fs/directory? old-path)
                          (rename-dir old-path new-path)
                          (if ((some-fn util/clj-file? util/cljs-file?)
                               (File. old-path))
                            (rename-source-file old-path new-path)
                            (rename-file! old-path new-path)))]
    (->> affected-files
         flatten
         distinct
         (map util/normalize-to-unix-path)
         (remove fs/directory?)
         (filter file-or-symlink-exists?)
         (into (list)))))

(defn- assert-friendly
  [old-path new-path]
  (let [exception-data {:old-path old-path :new-path new-path}]
    (cond
      (or (str/blank? old-path) (str/blank? new-path))
      (throw (ex-info "Can't act on empty path!" exception-data))

      (or (and (fs/file? old-path) (fs/directory? old-path))
          (and (fs/directory? old-path) (fs/file? old-path)))
      (throw (ex-info "Can only move dir to dir or file to file!" exception-data))

      (and (not (fs/directory? old-path))
           (not= (fs/extension old-path) (fs/extension new-path)))
      (throw (ex-info (str "Can't change file extension when moving! ")
                      exception-data)))))

(defn rename-file-or-dir
  "Renames a file or dir updating all dependent files.

  old-path and new-path are expected to be aboslute paths.

  Returns a list of all files that were affected."
  [old-path new-path]
  (assert-friendly old-path new-path)
  (binding [*print-length* nil]
    (-rename-file-or-dir old-path new-path)))
