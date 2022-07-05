;; PROJECT_VERSION is set by .circleci/deploy/deploy_release.clj,
;; whenever we perform a deployment.
(defproject refactor-nrepl (or (not-empty (System/getenv "PROJECT_VERSION"))
                               "0.0.0")
  :description "nREPL middleware to support editor-agnostic refactoring"
  :url "https://github.com/clojure-emacs/refactor-nrepl"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[nrepl "0.9.0"]
                 ^:inline-dep [compliment "0.3.12"]
                 ^:inline-dep [http-kit "2.5.3"]
                 ^:inline-dep [org.clojure/data.json "2.3.1"]
                 ^:inline-dep [org.clojure/tools.analyzer.jvm "1.2.2"]
                 ^:inline-dep [org.clojure/tools.namespace "1.1.0" :exclusions [org.clojure/tools.reader]]
                 ^:inline-dep [org.clojure/tools.reader "1.3.6"]
                 ^:inline-dep [cider/orchard "0.9.1"]
                 ^:inline-dep [cljfmt "0.8.0" :exclusions [rewrite-clj rewrite-cljs]]
                 ^:inline-dep [clj-commons/fs "1.6.310"]
                 ^:inline-dep [rewrite-clj "1.0.699-alpha"]
                 ^:inline-dep [version-clj "1.0.0"]]
  :exclusions [org.clojure/clojure] ; see versions matrix below

  :pedantic? ~(if (System/getenv "CI")
                :abort
                ;; :pedantic? can be problematic for certain local dev workflows:
                false)
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]
  :plugins [[thomasa/mranderson "0.5.4-SNAPSHOT"]]
  :mranderson {:project-prefix  "refactor-nrepl.inlined-deps"
               :expositions     [[org.clojure/tools.analyzer.jvm org.clojure/tools.analyzer]]
               :unresolved-tree false}
  :filespecs [{:type :bytes :path "refactor-nrepl/refactor-nrepl/project.clj" :bytes ~(slurp "project.clj")}]
  :profiles {;; Clojure versions matrix
             :provided {:dependencies [[cider/cider-nrepl "0.28.3"]
                                       [org.clojure/clojure "1.10.3"]
                                       ;; For satisfying `:pedantic?`:
                                       [com.google.code.findbugs/jsr305 "3.0.2"]
                                       [com.google.errorprone/error_prone_annotations "2.1.3"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.3"]]}

             :master {:repositories [["snapshots"
                                      "https://oss.sonatype.org/content/repositories/snapshots"]]
                      :dependencies [[org.clojure/clojure "1.11.0-master-SNAPSHOT"]
                                     [org.clojure/clojure "1.11.0-master-SNAPSHOT" :classifier "sources"]]}

             :test {:dependencies [[print-foo "1.0.2"]]}
             :dev {:dependencies [[org.clojure/clojurescript "1.10.879"]
                                  [org.clojure/core.async "1.3.618" :exclusions [org.clojure/clojure org.clojure/tools.reader]]
                                  [cider/piggieback "0.5.2"]
                                  [commons-io/commons-io "2.8.0"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :jvm-opts ["-Dorchard.use-dynapath=false"]
                   :java-source-paths ["java-test"]
                   :resource-paths ["test-resources"
                                    "testproject/src"]
                   :repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots"]]}
             :cljfmt [:test
                      {:plugins [[lein-cljfmt "0.8.0" :exclusions [org.clojure/clojure
                                                                   org.clojure/clojurescript]]]
                       :cljfmt {:indents {as-> [[:inner 0]]
                                          as->* [[:inner 0]]
                                          cond-> [[:inner 0]]
                                          cond->* [[:inner 0]]
                                          with-debug-bindings [[:inner 0]]
                                          merge-meta [[:inner 0]]
                                          try-if-let [[:block 1]]}}}]
             :eastwood {:plugins         [[jonase/eastwood "1.2.3"]]
                        :eastwood {;; :implicit-dependencies would fail spuriously when the CI matrix runs for Clojure < 1.10,
                                   ;; because :implicit-dependencies can only work for a certain corner case starting from 1.10.
                                   :exclude-linters [:implicit-dependencies]
                                   :exclude-namespaces [refactor-nrepl.plugin]
                                   :add-linters [:performance :boxed-math]
                                   :config-files ["eastwood.clj"]}}
             :clj-kondo [:test
                         {:dependencies [[clj-kondo "2022.06.22"]]}]

             :deploy {:source-paths [".circleci/deploy"]}}

  :jvm-opts ~(cond-> []
               (System/getenv "CI")
               (conj "-Drefactor-nrepl.internal.log-exceptions=true")))
