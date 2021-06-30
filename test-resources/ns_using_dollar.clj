(ns ns-using-dollar
  (:require [clojure.string :as string]
            [clojure.set :as set]
            [ns-with-dollar-symbol :refer [$]]))

$

(string/replace "foobar" "ob" "AZ")
