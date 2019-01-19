(ns ns-with-gen-class-methods-meta
  (:gen-class
   :methods [^:static [foo [String] String]
             ^:test [bar [String] String]
             ^{:other "text"} [baz [String] String]]
   :name Name)
  (:require [clojure.pprint :refer [fresh-line]]))
