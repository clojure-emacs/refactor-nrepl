(ns resources.ns1
  "This is a docstring for the ns"
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [#^{:static true} [binomial [int int] double]])
  (:require
   [clojure.instant :as inst]
   [clojure data edn]
   [clojure.pprint :refer [get-pretty-writer fresh-line cl-format]])
  (:use clojure.test
        clojure.test
        clojure.string)
  (:import java.util.Random
           java.io.PushbackReader
           [java.util Date Calendar]))

(defn use-everything []
  (get-pretty-writer)
  (fresh-line)
  (cl-format)
  (compose-fixtures)
  (escape)
  (inst/read-instant-date)
  (clojure.data/diff)
  (clojure.edn/read-string)
  (java.util.Random.)
  (java.util.Date.)
  (java.util.Calendar/getInstance)
  (java.io.PushbackReader. nil))
