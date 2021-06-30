(ns ns-with-rename
  "This is a docstring for the ns

  It contains an \"escaped string\"."
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:gen-class
   :name com.domain.tiny
   :extends java.lang.Exception
   :methods [[binomial [int int] double]])
  (:require
   [clojure.instant :as inst :refer [read-instant-date] :reload true]
   [clojure.walk :refer [prewalk postwalk]]
   (clojure data edn)
   [clojure.pprint :refer [get-pretty-writer formatter cl-format]]
   clojure.test.junit
   [clojure.xml])
  (:use clojure.test
        clojure.test
        [clojure.string :rename {replace foo split bar} :reload-all true :reload true])
  (:import java.util.Random
           java.io.PushbackReader
           java.io.PushbackReader
           java.io.FilenameFilter
           java.io.Closeable
           [java.util Date Date Calendar]
           (java.util Date Calendar)))

(foo "s" "match" "replacement")
