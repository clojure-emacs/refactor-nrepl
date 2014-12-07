(ns resources.ns2
  (:require
   [clojure
    [edn :refer :all :rename {read-string rs
                              read rd}]
    [string :refer :all :rename {replace foo
                                 reverse bar}]
    [test :refer :all]]
   [clojure.tools.analyzer :as ana]
   [clojure.tools.analyzer
    [ast :refer :all]
    [env :refer [deref-env with-env ]]]
    [clojure.tools.analyzer.passes.add-binding-atom :refer :all]))
