(ns refactor-nrepl.ns.tracker
  (:require [clojure.tools.namespace
             [dependency :as dep]
             [file :as file]
             [track :as tracker]]
            [refactor-nrepl.core :as core]))

(defn build-tracker
  "Build a tracker for the project."
  []
  (file/add-files (tracker/tracker) (core/find-in-project core/source-file?)))

(defn get-dependents
  "Get the dependent files for ns from tracker."
  [tracker my-ns]
  (let [deps (dep/immediate-dependents (:clojure.tools.namespace.track/deps tracker)
                                       (symbol my-ns))]
    (for [[file ns] (:clojure.tools.namespace.file/filemap tracker)
          :when ((set deps) ns)]
      file)))

(defn project-files-in-topo-order []
  (let [tracker (file/add-files (tracker/tracker) (core/find-in-project core/clj-file?))
        nses (dep/topo-sort (:clojure.tools.namespace.track/deps tracker))
        filemap (:clojure.tools.namespace.file/filemap tracker)
        ns2file (zipmap (vals filemap) (keys filemap))]
    (->> (map ns2file nses)
         (remove nil?))))
