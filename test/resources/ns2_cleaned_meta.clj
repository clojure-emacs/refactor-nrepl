(ns ^{:author "Trurl and Klapaucius", :doc "test ns with meta"}
 resources.ns2-meta
  (:require [clojure
             [edn :refer :all :rename {read rd, read-string rs}]
             [instant :refer :all]
             [pprint :refer [cl-format fresh-line get-pretty-writer]]
             [string :refer :all :rename {reverse bar, replace foo}]
             [test :refer :all]]))
