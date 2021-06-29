(ns ^:md1 ^:md2 ^:ok ^{:longhand "as well"
                       :really? "yes"}
 ns-with-gen-class-methods-meta
  (:gen-class
   :methods [^:static [foo [String] String]
             ^:test [bar [String] String]
             ^{:other "text"} [baz [String] String]]
   :name Name)
  (:require
   [clojure.pprint :refer [fresh-line]]
   [clojure.string :as s]))
