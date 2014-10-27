(ns refactor-nrepl.analyzer
  "This is a docstring for the ns"
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [#^{:static true} [binomial [int int] double]])
  (:require [clojure art data edn
             [instant :as inst]
             [string :refer :all]
             [test :refer :all]]
            [clojure.tools
             [analyzer :as ana]
             [reader :as r]]
            [clojure.tools.analyzer
             [ast :refer :all]
             [env :refer [with-env]]
             [jvm :as ana.jvm]]
            [clojure.tools.analyzer.passes
             [add-binding-atom :refer [add-binding-atom]]
             [cleanup :refer [cleanup]]
             [elide-meta :refer [elide-meta]]
             [source-info :refer [source-info]]
             [uniquify :refer [uniquify-locals]]
             [warn-earmuff :refer [warn-earmuff]]]
            [clojure.tools.analyzer.passes.jvm
             [analyze-host-expr :refer [analyze-host-expr]]
             [annotate-branch :refer [annotate-branch]]
             [annotate-class-id :refer [annotate-class-id]]
             [annotate-internal-name :refer [annotate-internal-name]]
             [annotate-loops :refer [annotate-loops]]
             [annotate-methods :refer [annotate-methods]]
             [annotate-tag :refer [annotate-tag]]
             [box :refer [box]]
             [classify-invoke :refer [classify-invoke]]
             [clear-locals :refer [clear-locals]]
             [constant-lifter :refer [constant-lift]]
             [fix-case-test :refer [fix-case-test]]
             [infer-tag :refer [ensure-tag infer-tag ]]
             [validate-loop-locals :refer [validate-loop-locals]]]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.tools.reader.reader-types :as rts])
  (:import java.io.PushbackReader
           [java.util Calendar Date Deque]))
