(ns ns2-cleaned
  (:require
   [clojure
    [edn :refer :all :rename {read-string rs}]
    [instant :refer :all]
    [pprint :refer [cl-format fresh-line get-pretty-writer]]
    [string :refer :all]
    [test :refer :all]]))
