(ns refactor-nrepl.bootstrap
  (:require [alembic.still :refer [distill make-still]]
            [classlojure.core :refer [base-classloader]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def repositories {"clojars" "http://clojars.org/repo"
                   "central" "http://repo1.maven.org/maven2/"})

(defn- core-dependencies []
  (let [profiles (->> (or (io/resource "refactor-nrepl-core/project.clj")
                          "refactor-nrepl-core/project.clj")
                      slurp
                      read-string
                      (drop-while #(not= % :profiles))
                      second)]
    (-> profiles :provided :dependencies)))

(defn init-classloader []
  (let [still (atom (make-still base-classloader))]
    (doseq [dep (core-dependencies)]
      (distill dep :repositories repositories :still still))
    (:classloader @still)))
