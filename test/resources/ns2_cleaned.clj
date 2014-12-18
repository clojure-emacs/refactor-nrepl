(ns resources.ns2-cleaned
  (:require
   [clojure
    [edn :refer :all :rename {read-string rs read rd}]
    [instant :refer :all]
    [pprint :refer [cl-format fresh-line get-pretty-writer]]
    [string :refer :all :rename {replace foo reverse bar}]
    [test :refer :all]]))
