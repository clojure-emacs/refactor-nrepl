(ns refactor-nrepl.bootstrap
  (:require [alembic.still :refer [distill make-still]]
            [classlojure.core
             :refer
             [base-classloader classlojure eval-in get-classpath]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [dynapath.util :refer [add-classpath-url]])
  (:import java.io.File
           java.util.regex.Pattern))

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
       (filter #(re-find #"org/clojure/clojure/.*/clojure-.*\.jar" %))
       first
       File.
       .toURI
       .toURL))

(defn- require-from-core [classloader]
  (eval-in classloader
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
  classloader)

(defn- project-file? [file-path]
  (-> "user.dir"
      System/getProperty
      str/lower-case
      Pattern/quote
      re-pattern
      (re-find file-path)))

(defn- normalize-path [file-path]
  "Get rid of leading / and lowercase"
  (-> file-path File. .getAbsolutePath str/lower-case))

(defn- add-host-project-to-classpath [still]
  (let [cl (:classloader @still)]
    (->>  base-classloader
          get-classpath
          (map normalize-path)
          (filter project-file?)
          (map #(-> % File. .toURI .toURL))
          (map (partial add-classpath-url cl))
          dorun)
    still))

(defn- load-core-dependencies
  [still]
  (doseq [dep (conj (core-dependencies)
                    []
                    ;; ['refactor-nrepl-core "0.3.0-SNAPSHOT"]
                    )]
    (distill dep :repositories repositories :still still :verbose false))
  (:classloader @still))

(defn- create-still []
  (atom (make-still (classlojure (clojure-jar-URL)
                                 (.. (File. "refactor-nrepl-core/src")
                                     toURI toURL)))))

(defn init-classloader []
  (-> (create-still)
      add-host-project-to-classpath
      load-core-dependencies
      require-from-core))
