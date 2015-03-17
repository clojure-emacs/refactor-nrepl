(ns refactor-nrepl.bootstrap
  "refactor-nrepl-core runs in an isolated classloader.  This ns is
  responsible for bootstrapping that classloader.  The following work is done:

  1. Create a fresh classloader.
  2. Use alembic to load refactor-nrepl-core and core's dependencies into the
     classloader.
  3. Add to the classpath of the classloader all the files from the original
     classpath which are found below the project root.
  4. Require all the namespaces in core to make the public API available for
     consumption with classlojure."
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

(defn- get-lein-project-file []
  (-> "user.dir" System/getProperty (str "/project.clj") slurp read-string))

(defn- dogfooding?
  "True when refactor-nrepl is being used to work on itself."
  []
  (= (second (get-lein-project-file))
     'refactor-nrepl))

(defn- core-dependencies []
  (let [profiles (->> (if (dogfooding?)
                        "refactor-nrepl-core/project.clj"
                        (io/resource "refactor-nrepl-core/project.clj"))
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

(defn- normalize-path
  "Get rid of leading / and lowercase"
  [file-path]
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

(defn- refactor-nrepl-artifact-vector []
  ['refactor-nrepl (nth (get-lein-project-file) 2)])

(defn- load-core-with-dependencies
  [still]
  (doseq [dep (if (dogfooding?)
                (core-dependencies)
                (conj (core-dependencies) (refactor-nrepl-artifact-vector)))]
    (distill dep :repositories repositories :still still :verbose false))
  (:classloader @still))

(defn- create-still []
  (atom (make-still (classlojure (clojure-jar-URL)
                                 (.. (File. "refactor-nrepl-core/src")
                                     toURI toURL)))))

(defn init-classloader
  "Create an isolated classloader containing refactor-nrepl-core and
  all of core's dependencies.

  The classloader's classpath is also seeded with all the classpath
  entries found below the project's root."
  []
  (-> (create-still)
      add-host-project-to-classpath
      load-core-with-dependencies
      require-from-core))
