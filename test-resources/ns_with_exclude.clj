(ns ns-with-exclude
  (:require [clojure.test :refer :all])
  (:use [clojure.test :exclude [test-ns]]))
