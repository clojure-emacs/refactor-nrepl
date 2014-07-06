(defproject clj-refactor-middleware "0.13.0-SNAPSHOT"
  :description "nREPL middleware to support refactorings in an editor agnostic way"
  :url "http://github.com/clojure-emacs/refactor-nrepl"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.clojure/tools.analyzer "0.2.3"]
                 [org.clojure/tools.analyzer.jvm "0.2.2"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [org.clojure/tools.reader "0.8.4"]]
  :profiles {:test {:dependencies [[print-foo "0.5.3"]]}})
