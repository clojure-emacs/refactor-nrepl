(defproject refactor-nrepl "2.4.0-SNAPSHOT"
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/tools.nrepl "0.2.13"]
                 ^:source-dep [http-kit "2.2.0"]
                 ^:source-dep [cheshire "5.7.1"]
                 ^:source-dep [alembic "0.3.2"]
                 ^:source-dep [org.clojure/tools.analyzer.jvm "0.7.1"]
                 ^:source-dep [org.clojure/tools.namespace  "0.3.0-alpha3"]
                 ^:source-dep [org.clojure/tools.reader "1.0.0"]
                 ^:source-dep [org.clojure/java.classpath "0.2.3"]
                 ^:source-dep [lein-cljfmt "0.3.0"]
                 ^:source-dep [me.raynes/fs "1.4.6"]
                 ^:source-dep [rewrite-clj "0.6.0"]
                 ^:source-dep [cljs-tooling "0.2.0"]
                 ^:source-dep [version-clj "0.1.2"]]
  :plugins [[thomasa/mranderson "0.4.7"]]
  :filespecs [{:type :bytes :path "refactor-nrepl/refactor-nrepl/project.clj" :bytes ~(slurp "project.clj")}]
  :profiles {:provided {:dependencies [[cider/cider-nrepl "0.14.0"]
                                       [org.clojure/clojure "1.7.0"]]}
             :test {:dependencies [[print-foo "1.0.2"]]
                    :src-paths ["test/resources"]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                                  [org.clojure/clojurescript "1.9.660"]]}
             :dev {:plugins [[jonase/eastwood "0.2.0"]]
                   :global-vars {*warn-on-reflection* true}
                   :dependencies [[org.clojure/clojurescript "1.7.48"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [leiningen-core "2.7.1"]
                                  [commons-io/commons-io "2.5"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :java-source-paths ["test/java"]
                   :resource-paths ["test/resources"
                                    "test/resources/testproject/src"]
                   :repositories [["snapshots" "http://oss.sonatype.org/content/repositories/snapshots"]]}})
