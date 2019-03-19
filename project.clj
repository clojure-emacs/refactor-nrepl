(defproject refactor-nrepl "2.4.1-SNAPSHOT"
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[nrepl "0.4.4"]
                 ^:source-dep [http-kit "2.3.0"]
                 ^:source-dep [cheshire "5.8.0"]
                 ^:source-dep [org.clojure/tools.analyzer.jvm "0.7.1"]
                 ^:source-dep [org.clojure/tools.namespace "0.3.0-alpha3"]
                 ;; Not used directly in refactor-nrepl, but needed because of tool.namespace
                 ;; and the way MrAnderson processes dependencies
                 ;; See https://github.com/clojure-emacs/cider/issues/2176 for details
                 ^:source-dep [org.clojure/java.classpath "0.2.3"]
                 ^:source-dep [org.clojure/tools.reader "1.1.1"]
                 ^:source-dep [cider/orchard "0.3.0"]
                 ^:source-dep [lein-cljfmt "0.3.0"]
                 ^:source-dep [me.raynes/fs "1.4.6"]
                 ^:source-dep [rewrite-clj "0.6.0"]
                 ^:source-dep [cljs-tooling "0.2.0"]
                 ^:source-dep [version-clj "0.1.2"]]
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]
  :plugins [[thomasa/mranderson "0.4.8"]]
  :filespecs [{:type :bytes :path "refactor-nrepl/refactor-nrepl/project.clj" :bytes ~(slurp "project.clj")}]
  :profiles {:provided {:dependencies [[cider/cider-nrepl "0.18.0"]
                                       [org.clojure/clojure "1.8.0"]]}
             :test {:dependencies [[print-foo "1.0.2"]]
                    :src-paths ["test/resources"]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.9.908"]]}
             :dev {:plugins [[jonase/eastwood "0.2.0"]]
                   :global-vars {*warn-on-reflection* true}
                   :dependencies [[org.clojure/clojurescript "1.9.89"]
                                  [cider/piggieback "0.3.8"]
                                  [leiningen-core "2.7.1"]
                                  [commons-io/commons-io "2.6"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :java-source-paths ["test/java"]
                   :resource-paths ["test/resources"
                                    "test/resources/testproject/src"]
                   :repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots"]]}}
  :jvm-opts ["-Djava.net.preferIPv4Stack=true"])
