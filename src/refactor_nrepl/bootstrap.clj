(ns refactor-nrepl.bootstrap
  (:require [alembic.still :refer [distill make-still]]
            [classlojure.core
             :refer [classlojure eval-in get-classpath base-classloader]]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import java.io.File))

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

(defn- clojure-jar-URL []
  (->> base-classloader
       get-classpath
       (filter #(re-find #"clojure-\d.\d.\d.jar" %))
       first
       File.
       .toURL))

(defn init-classloader []
  (let [still (atom (make-still (classlojure (clojure-jar-URL))))]
    (doseq [dep (conj (core-dependencies)
                      ['refactor-nrepl-core "0.3.0-SNAPSHOT"])]
      (distill dep :repositories repositories :still still))
    (eval-in (:classloader @still)
             '(do
                (require
                 '[refactor-nrepl-core.ns
                   [clean-ns :refer [clean-ns]]
                   [pprint :refer [pprint-ns]]
                   [resolve-missing :refer [resolve-missing]]])
                (require
                 '[refactor-nrepl-core
                   [analyzer :refer [find-unbound-vars]]
                   [artifacts :refer [artifact-versions artifacts-list]]
                   [find-symbol :refer [find-debug-fns find-symbol]]])))
    (:classloader @still)))
