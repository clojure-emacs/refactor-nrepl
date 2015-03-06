(def VERSION "0.3.0-SNAPSHOT")

(defproject refactor-nrepl VERSION
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.nrepl "0.2.7"]
                 ^:source-dep [http-kit "2.1.19"]
                 [org.clojure/data.json "0.2.5"]
                 ^:source-dep [instaparse "1.3.4"]
                 ^:source-dep [org.apache.httpcomponents/httpclient "4.3.5"]
                 ^:source-dep [org.clojure/tools.analyzer.jvm "0.6.5"]
                 ^:source-dep [org.clojure/tools.namespace "0.2.7"]
                 ^:source-dep [org.clojure/tools.reader "0.8.12"]
                 [org.projectodd.shimdandy/shimdandy-api "1.0.1"]]
  :plugins [[thomasa/mranderson "0.3.0"]]
  :filespecs [{:type :bytes :path "refactor-nrepl/refactor-nrepl/project.clj" :bytes ~(slurp "project.clj")}]
  :profiles {:provided {:dependencies [[cider/cider-nrepl "0.8.2"]]}
             :base {:resource-paths ["libs/"]}
             :test {:dependencies [[print-foo "1.0.1"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha5"]]}
             :dev {:plugins [[jonase/eastwood "0.2.0"]]
                   :dependencies [[me.raynes/fs "1.4.6"]]
                   :resource-paths ["test/resources"
                                    "resources/testproject/src"]
                   :repositories [["snapshots" "http://oss.sonatype.org/content/repositories/snapshots"]]}})
