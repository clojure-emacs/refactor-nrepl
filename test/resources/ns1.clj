(ns refactor-nrepl.analyzer
  "This is a docstring for the ns"
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [#^{:static true} [binomial [int int] double]])
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.reader :as r]
            [clojure.tools.analyzer.passes.jvm
             [box :refer [box]]
             [constant-lifter :refer [constant-lift]]
             [annotate-branch :refer [annotate-branch]]
             [annotate-internal-name :refer [annotate-internal-name]]
             [fix-case-test :refer [fix-case-test]]
             [clear-locals :refer [clear-locals]]
             [classify-invoke :refer [classify-invoke]]
             [annotate-loops :refer [annotate-loops]]
             [annotate-methods :refer [annotate-methods]]
             [annotate-class-id :refer [annotate-class-id]]
             [infer-tag :refer [infer-tag ensure-tag]]
             [annotate-tag :refer [annotate-tag]]
             [validate-loop-locals :refer [validate-loop-locals]]
             [analyze-host-expr :refer [analyze-host-expr]]]
            [clojure.instant :as inst]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.analyzer.env :refer [with-env]]
            [clojure data edn art]
            [clojure.tools.analyzer.passes
             [source-info :refer [source-info]]
             [cleanup :refer [cleanup]]
             [elide-meta :refer [elide-meta]]
             [warn-earmuff :refer [warn-earmuff]]
             [add-binding-atom :refer [add-binding-atom]]
             [uniquify :refer [uniquify-locals]]]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.tools.reader.reader-types :as rts])
  (:use clojure.test
        clojure.test
        clojure.string)
  (:import java.util.Deque
           java.io.PushbackReader
           [java.util Date Calendar]))
