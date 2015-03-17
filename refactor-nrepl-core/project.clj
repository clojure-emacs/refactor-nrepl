(defproject refactor-nrepl-core "0.3.0-SNAPSHOT"
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :filespecs [{:type :bytes :path "refactor-nrepl/refactor-nrepl/project.clj"
               :bytes ~(slurp "project.clj")}
              {:type :bytes :path "refactor-nrepl-core/project.clj"}]
  :profiles {:provided {:dependencies [[cider/cider-nrepl "0.8.2"]
                                       [http-kit "2.1.19"]
                                       [instaparse "1.3.4"]
                                       [org.clojure/data.json "0.2.5"]
                                       [org.clojure/tools.analyzer.jvm "0.6.5"]
                                       [org.clojure/tools.namespace "0.2.7"]
                                       [org.clojure/tools.nrepl "0.2.8"]
                                       [org.clojure/tools.reader "0.8.12"]]}
             :test {:dependencies [[org.tcrawley/dynapath "0.2.3"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha5"]]}
             :dev {:resource-paths ["test/resources"
                                    "resources/testproject/src"]
                   :repositories [["snapshots" "http://oss.sonatype.org/content/repositories/snapshots"]]}})
