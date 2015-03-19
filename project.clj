(defproject refactor-nrepl "0.3.0-SNAPSHOT"
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[alembic "0.3.2"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.nrepl "0.2.7"]
                 [org.flatland/classlojure "0.7.1"]]
  :filespecs [{:type :bytes :path "refactor-nrepl-core/project.clj"
               :bytes ~(slurp "refactor-nrepl-core/project.clj")}]
  :java-source-paths ["src/java"]
  :profiles {:dev {:dependencies [[me.raynes/fs "1.4.6"]
                                  [cider/cider-nrepl "0.8.2"]
                                  [http-kit "2.1.19"]
                                  [org.clojure/data.json "0.2.5"]
                                  [instaparse "1.3.4"]
                                  [org.clojure/tools.analyzer.jvm "0.6.5"]
                                  [org.clojure/tools.namespace "0.2.7"]
                                  [org.clojure/tools.reader "0.8.12"]]
                   :resource-paths ["resources/testproject/src"]
                   :source-paths ["src" "test" "refactor-nrepl-core/src"]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha5"]]}})
