(ns ns1
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
        [clojure.string :rename {replace foo reverse bar} :reload-all true :reload true])
  (:import java.util.Random
           java.io.PushbackReader
           java.io.PushbackReader
           java.io.FilenameFilter
           java.io.Closeable
           [java.util Date Date Calendar]
           [refactor.nrepl
            SomeClass$InnerClass$InnerInnerClassOne
            SomeClass$InnerClass$InnerInnerClassTwo
            SomeClass$InnerClass$InnerInnerClassThree]
           (java.util Date Calendar)))

(defmacro tt [writer]
  (Random.)
  `(get-pretty-writer ~writer))

(defmacro black-hole [& body])

(black-hole
 (prewalk identity [1 2 3])
 (postwalk identity [3 2 1]))

(defn use-everything [^Closeable whatever]
  (cl-format)
  (formatter nil)
  (compose-fixtures)
  (clojure.test.junit/with-junit-output "")
  (escape)
  ::inst/namespaced-keyword-prevents-pruning
  (SomeClass$InnerClass$InnerInnerClassOne.)
  (SomeClass$InnerClass$InnerInnerClassTwo.)
  (clojure.data/diff)
  (clojure.edn/read-string)
  (clojure.xml/emit "")
  (Date.)
  (Calendar/getInstance)
  (PushbackReader. nil))

(proxy [FilenameFilter] []
  (accept [d n] true))
