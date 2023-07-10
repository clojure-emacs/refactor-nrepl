(ns refactor-nrepl.ns.tracker
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.namespace.dependency :as dep]
   [clojure.tools.namespace.file :as file]
   [clojure.tools.namespace.repl :as tools.namespace.repl]
   [clojure.tools.namespace.track :as tracker]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.util :as util])
  (:import
   (java.io File)))

;; Adapted from CIDER: https://git.io/JOYUT
(defn- user-refresh-dirs
  "Directories to watch and reload, as configured by the user.
  See `clojure.tools.namespace.repl/set-refresh-dirs`.
  The var is resolved at runtime to get the \"real\" clojure.tools.namespace,
  not the mranderson-ized version bundled with refactor-nrepl. Returns `nil` if c.t.n.r
  isn't loaded. Returns `[]` if c.t.n.r is loaded but no custom dirs have been
  set."
  []
  (or (some-> (symbol "clojure.tools.namespace.repl" "refresh-dirs")
              resolve
              deref)
      ;; corner case - use the mranderson-ized refresh-dirs (for supporting this project's test suite):
      tools.namespace.repl/refresh-dirs))

(def default-file-filter-predicate core/source-file?)

(defn build-tracker
  "Build a tracker for the project.

  If file-predicate is provided, use that instead of `core/source-file?`"
  ([]
   (build-tracker default-file-filter-predicate))
  ([file-predicate]
   (file/add-files (tracker/tracker)
                   (core/find-in-project file-predicate (core/source-dirs-on-classpath)))))

(defn get-dependents
  "Get the dependent files for ns from tracker."
  [tracker my-ns]
  (let [deps (dep/immediate-dependents (:clojure.tools.namespace.track/deps tracker)
                                       (symbol my-ns))
        deps-set (set deps)]
    (for [[file ns] (:clojure.tools.namespace.file/filemap tracker)
          :when (and (not (util/interrupted?))
                     (deps-set ns))]
      file)))

(defn- absolutize-dirs [dirs]
  (->> dirs
       (map (fn [^String s]
              (File. s)))
       (filter (fn [^File f]
                 (.exists f)))
       (map (fn [^File f]
              (let [v (.getCanonicalPath f)]
                (cond-> v
                  (not (str/ends-with? v File/separator))
                  ;; add a trailing slash for a more robust comparison with `file-as-absolute-paths`:
                  (str File/separator)))))))

(defn in-refresh-dirs?
  "Is `filename` located within any of `refresh-dirs`?"
  [refresh-dirs refresh-dirs-as-absolute-paths filename]
  (if-not (seq refresh-dirs)
    ;; the end user has not set the `refresh-dirs`, so this defn's logic should be bypassed:
    true
    (let [file (-> filename io/file)
          file-as-absolute-path (-> file .getCanonicalPath)]
      (and (-> file .exists)
           (-> file .isFile)
           (boolean (some (partial str/starts-with? file-as-absolute-path)
                          refresh-dirs-as-absolute-paths))))))

(defn project-files-in-topo-order
  ([]
   (project-files-in-topo-order false))
  ([ignore-errors?]
   (let [refresh-dirs (user-refresh-dirs)
         tracker (build-tracker (util/with-suppressed-errors
                                  (every-pred (partial in-refresh-dirs? refresh-dirs (absolutize-dirs refresh-dirs))
                                              core/clj-file?)
                                  ignore-errors?))
         nses (dep/topo-sort (:clojure.tools.namespace.track/deps tracker))
         filemap (:clojure.tools.namespace.file/filemap tracker)
         ns->file (zipmap (vals filemap) (keys filemap))]
     (keep ns->file nses))))
