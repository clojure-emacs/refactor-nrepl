(set-env!
  :resource-paths #{"src"}
  :dependencies '[[org.clojure/tools.nrepl "0.2.6"]
                  [http-kit "2.1.19"]
                  [org.clojure/data.json "0.2.5"]
                  [boot/core "2.0.0-rc8"]
                  [boot/base "2.0.0-rc8"]
                  [boot/aether "2.0.0-rc8"]
                  [instaparse "1.3.4"]
                  [org.clojure/tools.analyzer.jvm "0.6.5"]
                  [org.clojure/tools.namespace "0.2.7"]
                  [org.clojure/tools.reader "0.8.12"]
                  [adzerk/boot-test "1.0.3" :scope "test"]
                  [me.raynes/fs "1.4.6" :scope "test"]
                  [cider/cider-nrepl "0.8.2" :scope "provided"]])

(require '[adzerk.boot-test :refer :all])

(deftask testprofile
  "modifies environment for testing"
  []
  (set-env! :source-paths #(conj % "test")
            :resource-paths #(conj % "test/resources" "resources/testproject/src"))
  identity)

(deftask test-all
  "runs all tests"
  []
  (comp (testprofile) (test :namespaces #{'refactor-nrepl.integration-tests 'refactor-nrepl.test-artifacts 'refactor-nrepl.test-namespace})))

(task-options!
  pom {:project 'refactor-nrepl
       :version "0.3.0-BOOT"
       :description "nREPL middleware to support editor-agnostic refactoring"
       :url "http://github.com/clojure-emacs/refactor-nrepl"
       :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})
