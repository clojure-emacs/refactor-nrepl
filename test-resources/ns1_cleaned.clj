(ns ns1-cleaned
  "This is a docstring for the ns

  It contains an \"escaped string\"."
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [[binomial [int int] double]])
  (:require [clojure data edn
             [instant :as inst :reload true]
             [pprint :refer [cl-format formatter get-pretty-writer]]
             [string :refer :all :reload-all true]
             [test :refer :all]
             [walk :refer [postwalk prewalk]]
             xml]
            clojure.test.junit)
  (:import
   [java.io Closeable FilenameFilter PushbackReader]
   [java.util Calendar Date Random]
   [refactor.nrepl
    SomeClass$InnerClass$InnerInnerClassOne
    SomeClass$InnerClass$InnerInnerClassTwo]))
