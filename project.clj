(defproject refactor-nrepl "2.5.1"
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[nrepl "0.8.3"]
                 ^:inline-dep [http-kit "2.5.1"]
                 ^:inline-dep [cheshire "5.10.0"]
                 ^:inline-dep [org.clojure/tools.analyzer.jvm "1.1.0"]
                 ^:inline-dep [org.clojure/tools.namespace "1.1.0" :exclusions [org.clojure/tools.reader]]
                 ^:inline-dep [org.clojure/tools.reader "1.3.5"]
                 ^:inline-dep [cider/orchard "0.6.5"]
                 ^:inline-dep [cljfmt "0.7.0"]
                 ^:inline-dep [clj-commons/fs "1.6.307"]
                 ^:inline-dep [rewrite-clj "0.6.1"]
                 ^:inline-dep [version-clj "1.0.0"]]
  :exclusions [org.clojure/clojure] ; see versions matrix below

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]
  :plugins [[thomasa/mranderson "0.5.3"]]
  :mranderson {:project-prefix  "refactor-nrepl.inlined-deps"
               :expositions     [[org.clojure/tools.analyzer.jvm org.clojure/tools.analyzer]]
               :unresolved-tree false}
  :filespecs [{:type :bytes :path "refactor-nrepl/refactor-nrepl/project.clj" :bytes ~(slurp "project.clj")}]
  :profiles {;; Clojure versions matrix
             :provided {:dependencies [[cider/cider-nrepl "0.25.9"]
                                       [org.clojure/clojure "1.8.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.8.51"]
                                  [javax.xml.bind/jaxb-api "2.3.1"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.9.946"]
                                  [javax.xml.bind/jaxb-api "2.3.1"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.2"]
                                   [org.clojure/clojurescript "1.10.520"]]}

             :master {:repositories [["snapshots"
                                      "https://oss.sonatype.org/content/repositories/snapshots"]]
                      :dependencies [[org.clojure/clojure "1.11.0-master-SNAPSHOT"]
                                     [org.clojure/clojure "1.11.0-master-SNAPSHOT" :classifier "sources"]]}

             :lein-plugin {:source-paths ["lein-plugin"]}
             :test {:dependencies [[print-foo "1.0.2"]]
                    :src-paths ["test/resources"]}
             :dev {:global-vars {*warn-on-reflection* true}
                   :dependencies [[org.clojure/clojurescript "1.9.946"]
                                  [cider/piggieback "0.5.2"]
                                  [commons-io/commons-io "2.8.0"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :java-source-paths ["test/java"]
                   :resource-paths ["test/resources"
                                    "test/resources/testproject/src"]
                   :repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots"]]}
             :cljfmt [:test
                      {:plugins [[lein-cljfmt "0.7.0"]]
                       :cljfmt {:indents {as-> [[:inner 0]]
                                          as->* [[:inner 0]]
                                          cond-> [[:inner 0]]
                                          cond->* [[:inner 0]]
                                          with-debug-bindings [[:inner 0]]
                                          merge-meta [[:inner 0]]
                                          try-if-let [[:block 1]]}}}]
             :eastwood {:plugins         [[jonase/eastwood "0.4.0"]]
                        ;; TODO: add :test-paths
                        :eastwood {:namespaces      [:source-paths]
                                   ;; vendored - shouldn't be tweaked for satisfying linters:
                                   :exclude-namespaces [refactor-nrepl.ns.slam.hound.regrow]
                                   :exclude-linters [:unused-ret-vals]}}
             :clj-kondo [:test
                         {:dependencies [[clj-kondo "2021.03.31"]]}]}
  :jvm-opts ["-Djava.net.preferIPv4Stack=true"])
