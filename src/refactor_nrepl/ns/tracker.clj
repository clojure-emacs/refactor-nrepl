(ns refactor-nrepl.ns.tracker
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace
             [dependency :as dep]
             [file :as file]
             [track :as tracker]]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.ns.ns-parser :as ns-parser]))

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

(defn build-tracker
  "Build a tracker for the project.

  If file-predicate is provided, use that instead of `core/source-file?`"
  ([]
   (build-tracker #(and (core/source-file? %)
                        (safe-for-clojure-tools-namespace? %))))
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

(defn project-files-in-topo-order []
  (let [tracker (build-tracker core/clj-file?)
        nses (dep/topo-sort (:clojure.tools.namespace.track/deps tracker))
        filemap (:clojure.tools.namespace.file/filemap tracker)
        ns2file (zipmap (vals filemap) (keys filemap))]
    (->> (map ns2file nses)
         (remove nil?))))
