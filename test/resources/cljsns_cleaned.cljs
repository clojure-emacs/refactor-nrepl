(ns resources.cljsns
  (:require [cljs.pprint :as pprint]
            [cljs.test :refer-macros [deftest is]]
            [clojure.set :as set]
            [clojure.string :refer [join split-lines]])
  (:require-macros [cljs.test :refer [run-tests testing]])
  (:import goog.string))
