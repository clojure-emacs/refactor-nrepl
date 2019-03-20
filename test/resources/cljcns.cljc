(ns resources.cljcns
  "This is a docstring for the ns"
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  #?@(:clj
      [(:require
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
                (java.util Date Calendar))]
      :cljs
      [(:require [cljs.test :refer-macros [is deftest]]
                 [cljs.test :refer-macros [is]]
                 [clojure.string :refer [split-lines join]]
                 [cljs.pprint :as pprint]
                 [clojure.set :as set])
       (:require-macros [cljs.test :refer [testing]]
                        [cljs.analyzer.macros :as am]
                        cljs.analyzer.api)
       (:use-macros [cljs.test :only [run-tests]])
       (:import goog.string)]))

#?(:cljs
   (do
     (defn use-some-of-it []
       (pprint/pprint {:foo :bar})
       (set/intersection #{1 2} #{1})
       (split-lines "ok"))

     (deftest tt
       (testing "whatever"
         (is (= 1 1))))

     (defn foo []
       `(join "foo bar"))

     (fn []
       (run-tests))

     (string/regExpEscape "ok")

     (am/with-core-macros "fake/path"
       :ignore)

     (cljs.analyzer.api/no-warn
      :body))
   :clj
   (do
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
       (inst/read-instant-date)
       (clojure.data/diff)
       (clojure.edn/read-string)
       (clojure.xml/emit "")
       (Date.)
       (Calendar/getInstance)
       (PushbackReader. nil))

     (proxy [FilenameFilter] []
       (accept [d n] true))))
