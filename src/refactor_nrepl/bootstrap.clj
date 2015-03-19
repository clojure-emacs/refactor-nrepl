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
            [dynapath.util :refer [add-classpath-url]]
            [clojure.java.io :refer [as-url as-file]])
  (:import java.io.File
           java.util.regex.Pattern
           refactor_nrepl.PostDelegationClassLoader
           java.net.URL))

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

(defn- require-from-core [classloader]
  (eval-in classloader
           '(do
              (in-ns 'utter-isolation)
              (clojure.core/require
               '[refactor-nrepl-core.ns
                 [clean-ns :refer [clean-ns]]
                 [pprint :refer [pprint-ns]]
                 [resolve-missing :refer [resolve-missing]]])
              (clojure.core/require
               '[refactor-nrepl-core
                 [analyzer :refer [find-unbound-vars]]
                 [artifacts :refer [artifact-versions artifacts-list]]
                 [find-symbol :refer [find-debug-fns find-symbol]]])))
  classloader)

(defn- refactor-nrepl-artifact-vector []
  ['refactor-nrepl (nth (get-lein-project-file) 2)])

(defn- load-core-with-dependencies
  [still]
  (doseq [dep (if (dogfooding?)
                (core-dependencies)
                (conj (core-dependencies) (refactor-nrepl-artifact-vector)))]
    (distill dep :repositories repositories :still still :verbose false))
  (:classloader @still))

(defn- create-post-delegating-classloader []
  (->> base-classloader
       (PostDelegationClassLoader.
        (if (dogfooding?)
          (into-array URL [(-> "refactor-nrepl-core/src/" as-file as-url)])
          (make-array URL 0)))))

(defn- create-still []
  (atom (make-still (create-post-delegating-classloader))))

(defn init-classloader
  "Create an isolated classloader containing refactor-nrepl-core and
  all of core's dependencies.

  The classloader's classpath is also seeded with all the classpath
  entries found below the project's root."
  []
  (-> (create-still)
      load-core-with-dependencies
      require-from-core))
