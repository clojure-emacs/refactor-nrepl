(ns resources.cljsns
  (:require [cljs.test :refer-macros [is deftest]]
            [cljs.test :refer-macros [is]]
            [clojure.string :refer [split-lines join]]
            [cljs.pprint :as pprint]
            [clojure.set :as set])
  (:require-macros [cljs.test :refer [testing]])
  (:use-macros [cljs.test :only [run-tests]])
  (:import goog.string))

(defn use-some-of-it []
  (pprint/pprint {:foo :bar})
  (set/intersection #{1 2} #{1})
  (split-lines "ok"))

(deftest tt
  (testing "whatever"
      (is (= 1 1))))

(defn foo []
  `(join "foo bar"))

(fn []
  (run-tests))

(string/regExpEscape "ok")
