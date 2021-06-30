(ns cljcns
  "This is a docstring for the ns"
  {:author "Winnie the pooh"}
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  #?@(:clj
      [(:require clojure.data clojure.edn
                 [clojure.instant :as inst :reload true]
                 [clojure.pprint :refer [cl-format formatter get-pretty-writer]]
                 [clojure.string :refer :all :reload-all true]
                 [clojure.test :refer :all]
                 clojure.test.junit
                 [clojure.walk :refer [postwalk prewalk]]
                 clojure.xml)
       (:import [java.io Closeable FilenameFilter PushbackReader]
                [java.util Calendar Date Random])]
      :cljs
      [(:require [cljs.pprint :as pprint]
                 [cljs.test :refer-macros [deftest is]]
                 [clojure.set :as set]
                 [clojure.string :refer [join split-lines]])
       (:require-macros cljs.analyzer.api
                        [cljs.analyzer.macros :as am]
                        [cljs.test :refer [run-tests testing]])
       (:import goog.string)]))
