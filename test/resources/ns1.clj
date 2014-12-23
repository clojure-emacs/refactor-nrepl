(ns resources.ns1
  "This is a docstring for the ns"
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [[binomial [int int] double]])
  (:require
   [clojure.instant :as inst]
   [clojure data edn]
   [clojure.pprint :refer [get-pretty-writer formatter cl-format]]
   clojure.test.junit)
  (:use clojure.test
        clojure.test
        [clojure.string :rename {replace foo
                                 reverse bar}])
  (:import java.util.Random
           java.io.PushbackReader
           [java.util Date Calendar]))

(defn use-everything []
  (get-pretty-writer)
  (cl-format)
  (formatter nil)
  (compose-fixtures)
  (clojure.test.junit/with-junit-output "")
  (escape)
  (inst/read-instant-date)
  (clojure.data/diff)
  (clojure.edn/read-string)
  (Random.)
  (Date.)
  (Calendar/getInstance)
  (PushbackReader. nil))
