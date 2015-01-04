(ns resources.ns1-cleaned
  "This is a docstring for the ns"
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [[binomial [int int] double]])
  (:require [clojure data edn
             [instant :as inst]
             [pprint :refer [cl-format formatter get-pretty-writer]]
             [string :refer :all :rename {replace foo reverse bar}]
             [test :refer :all]
             xml]
            clojure.test.junit)
  (:import java.io.PushbackReader
           [java.util Calendar Date Random]))
