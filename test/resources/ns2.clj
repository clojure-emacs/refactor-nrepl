(ns resources.ns2
  (:require
   [clojure.tools.analyzer.ast :refer :all]
   [clojure.tools.analyzer.ast :refer [children* children]]
   [clojure.tools.analyzer :as ana]
   [clojure.tools.analyzer.env :refer [with-env]]
   [clojure.tools.analyzer.env :refer [deref-env]]
   [clojure.tools.analyzer.passes.add-binding-atom :refer :all])
  (:use clojure.test
        clojure.test
        [clojure.string :rename {replace foo
                                 reverse bar}]
        [clojure.edn :rename {read-string rs
                              read rd}]))
