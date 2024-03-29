(ns cljsns
  (:require [cljs.test :refer-macros [is deftest]]
            [cljs.test :refer-macros [is]]
            [cljsjs.js-yaml] ; this one should not be pruned as it contains externs
            [clojure.string :refer [split-lines join]]
            [cljs.pprint :as pprint]
            [js-literal-ns :as js-literal]
            [keyword-ns :as kw]
            [clojure.set :as set]
            ["react" :as react]
            ["underscore$default" :as underscore]
            ["react-UNUSED" :as react-unused]
            ["underscore-UNUSED$default" :as underscore-unused]
            ["@react-native-async-storage/async-storage" :as AsyncStorage])
  (:require-macros [cljs.test :refer [testing]]
                   [cljs.analyzer.macros :as am]
                   cljs.analyzer.api)
  (:use-macros [cljs.test :only [run-tests]])
  (:import goog.string))

(defn use-some-of-it []
  (pprint/pprint {:foo :bar})
  (set/intersection #{1 2} #{1})
  (split-lines "ok"))

(defn use-string-requires
  "Uses string requires as found in the ns declaration.

  Does not use the stuff marked as UNUSED, which therefore should be removed."
  []
  (react/foo underscore/bar))

;; https://github.com/clojure-emacs/clj-refactor.el/issues/529
(defn use-as
  "Uses an `:as` name as an object in itself"
  []
  (.getItem AsyncStorage "foo"))

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

#js [{:foo #js [::js-literal/bar]}]

;; Caused reader to crash for cljs
;; https://github.com/clojure-emacs/clj-refactor.el/issues/353
::kw/foo
