(ns ^{:author "Trurl and Klapaucius"
      :doc "test ns with meta"}
  resources.ns2-meta
  (:require
   [clojure.pprint :refer [fresh-line]]
   [clojure.pprint :refer [get-pretty-writer]]
   [clojure.pprint :refer [cl-format]]
   [clojure.pprint :refer [get-pretty-writer fresh-line cl-format]]
   [clojure.instant :refer [read-instant-calendar]]
   [clojure.instant :refer :all]
   [clojure.test.junit :refer [with-junit-output]])
  (:use clojure.test
        clojure.test
        [clojure.string :rename {replace foo
                                 reverse bar}]
        [clojure.edn :rename {read-string rs
                              read rd}]))
(defn use-everything []
  (get-pretty-writer)
  (fresh-line)
  (cl-format)
  (compose-fixtures)
  (escape)
  (read-instant-date)
  (rs))
