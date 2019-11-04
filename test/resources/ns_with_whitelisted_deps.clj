(ns resources.ns-with-whitelisted-deps
  (:require [^:side-effects clojure.set :as set]
            ^:side-effects [clojure.string :as str]
            ^:side-effects clojure.pprint
            [resources
             ^:side-effects ns1
             ^:side-effects ns2]
            ^:side-effects
            [clojure
             [data :as data]
             [walk :as walk]])
  (:import ^:side-effects java.util.Date
           ^:side-effects [java.io BufferedReader Reader]
           [java.sql
            ^:side-effects Timestamp
            ^:side-effects Time]))
