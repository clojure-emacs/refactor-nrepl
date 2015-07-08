(defproject refactor-nrepl "1.2.0-SNAPSHOT"
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/tools.nrepl "0.2.10"]
                 ^:source-dep [http-kit "2.1.19"]
                 ^:source-dep [cheshire "5.4.0"]
                 ^:source-dep [alembic "0.3.2"]
                 ^:source-dep [instaparse "1.3.6"]
                 ^:source-dep [org.clojure/tools.analyzer.jvm "0.6.6"]
                 ^:source-dep [org.clojure/tools.namespace "0.2.7"]
                 ^:source-dep [org.clojure/tools.reader "0.8.12"]
                 ^:source-dep [org.clojure/java.classpath "0.2.2"]
                 ^:source-dep [me.raynes/fs "1.4.6"]]
  :plugins [[thomasa/mranderson "0.4.0"]]
  :filespecs [{:type :bytes :path "refactor-nrepl/refactor-nrepl/project.clj" :bytes ~(slurp "project.clj")}]
  :profiles {:provided {:dependencies [[cider/cider-nrepl "0.9.0"]]}
             :test {:dependencies [[print-foo "1.0.1"]]
                    :src-paths ["test/resources"]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :dev {:plugins [[jonase/eastwood "0.2.0"]]
                   :dependencies [[org.clojure/clojure "1.7.0"]
                                  [commons-io/commons-io "2.4"]]
                   :java-source-paths ["test/java"]
                   :resource-paths ["test/resources"
                                    "test/resources/testproject/src"]
                   :repositories [["snapshots" "http://oss.sonatype.org/content/repositories/snapshots"]]}})
