(ns resources.ns-using-dollar
  (:require [clojure.string :as string]
            [clojure.set :as set]
            [resources.ns-with-dollar-symbol :refer [$]]))

$

(string/replace "foobar" "ob" "AZ")
