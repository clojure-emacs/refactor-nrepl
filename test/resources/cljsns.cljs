(ns resources.cljsns
  (:require [cljs.test :refer-macros [is deftest]]
            [cljs.test :refer-macros [is]]
            [cljsjs.js-yaml] ; this one should not be pruned as it contains externs
            [clojure.string :refer [split-lines join]]
            [cljs.pprint :as pprint]
            [clojure.set :as set])
  (:require-macros [cljs.test :refer [testing]]
                   [cljs.analyzer.macros :as am]
                   cljs.analyzer.api)
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

(am/with-core-macros "fake/path"
  :ignore)

(cljs.analyzer.api/no-warn
 :body)
