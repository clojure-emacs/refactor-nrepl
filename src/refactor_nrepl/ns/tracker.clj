(ns refactor-nrepl.ns.tracker
  (:require [clojure.tools.namespace
             [dependency :as dep]
             [file :as file]
             [track :as tracker]]
            [refactor-nrepl.util :as util]))

(defn build-tracker
  "Build a tracker for the project."
  []
  (let [tracker (tracker/tracker)]
    (file/add-files tracker (util/find-clojure-sources-in-project))))

(defn get-dependents
  "Get the dependent files for ns from tracker."
  [tracker my-ns]
  (let [deps (dep/immediate-dependents (:clojure.tools.namespace.track/deps tracker)
                                       (symbol my-ns))]
    (for [[file ns] (:clojure.tools.namespace.file/filemap tracker)
          :when ((set deps) ns)]
      file)))
