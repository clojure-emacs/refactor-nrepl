(ns resources.ns-with-whitelisted-deps
  (:require [clojure
             [data :as data]
             pprint
             [set :as set]
             [walk :as walk]]
            [resources
             ns1
             ns2])
  (:import [java.io BufferedReader Reader]
           [java.sql Time Timestamp]
           java.util.Date))
