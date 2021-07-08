(ns refactor-nrepl.ns.tracker
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.namespace
             [dependency :as dep]
             [file :as file]
             [repl :refer [refresh-dirs]]
             [track :as tracker]]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.util :as util]
            [refactor-nrepl.ns.ns-parser :as ns-parser])
  (:import [java.io File]))

;; Exclude cljs files that use npm string requires until they fix this bug:
;; https://clojure.atlassian.net/projects/TNS/issues/TNS-51
(defn- safe-for-clojure-tools-namespace? [f]
  (->> (io/file f)
       (.getAbsolutePath)
       ns-parser/parse-ns
       :cljs
       :require
       (map :ns)
       (not-any? string?)))

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
      refresh-dirs))

(def default-file-filter-predicate (every-pred core/source-file?
                                               safe-for-clojure-tools-namespace?))

(defn build-tracker
  "Build a tracker for the project.

  If file-predicate is provided, use that instead of `core/source-file?`"
  ([]
   (build-tracker default-file-filter-predicate))
  ([file-predicate]
   (file/add-files (tracker/tracker) (core/find-in-project file-predicate))))

(defn get-dependents
  "Get the dependent files for ns from tracker."
  [tracker my-ns]
  (let [deps (dep/immediate-dependents (:clojure.tools.namespace.track/deps tracker)
                                       (symbol my-ns))]
    (for [[file ns] (:clojure.tools.namespace.file/filemap tracker)
          :when ((set deps) ns)]
      file)))

(defn- in-refresh-dirs? [refresh-dirs file]
  (if-not (seq refresh-dirs)
    ;; the end user has not set the `refresh-dirs`, so this defn's logic should be bypassed:
    true
    (let [file-as-absolute-paths (-> file io/file .getCanonicalPath)
          refresh-dirs-as-absolute-paths (->> refresh-dirs
                                              (map (fn [^String s]
                                                     (File. s)))
                                              (filter (fn [^File f]
                                                        (.exists f)))
                                              (map (fn [^File f]
                                                     (let [v (.getCanonicalPath f)]
                                                       (cond-> v
                                                         (not (str/ends-with? v File/separator))
                                                         ;; add a trailing slash for a more robust comparison with `file-as-absolute-paths`:
                                                         (str File/separator))))))]
      (boolean (some #(str/starts-with? % file-as-absolute-paths)
                     refresh-dirs-as-absolute-paths)))))

(defn project-files-in-topo-order
  ([]
   (project-files-in-topo-order false))
  ([ignore-errors?]
   (let [tracker (build-tracker (util/with-suppressed-errors
                                  (every-pred (partial in-refresh-dirs? (user-refresh-dirs))
                                              core/clj-file?)
                                  ignore-errors?))
         nses (dep/topo-sort (:clojure.tools.namespace.track/deps tracker))
         filemap (:clojure.tools.namespace.file/filemap tracker)
         ns2file (zipmap (vals filemap) (keys filemap))]
     (->> (map ns2file nses)
          (remove nil?)))))
