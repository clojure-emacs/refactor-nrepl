(ns refactor-nrepl.ns.namespace-aliases-test
  (:require [clojure.test :as t]
            [refactor-nrepl.ns
             [helpers :as util]
             [namespace-aliases :as sut]]
            [refactor-nrepl.util :as rutil]))

(t/deftest finds-the-aliases-in-this-ns
  (let [aliases (:clj (sut/namespace-aliases))]
    (t/is (some (fn [alias]
                  (and (= (first alias) 'sut)
                       (= (some #(= % 'refactor-nrepl.ns.namespace-aliases)
                                (second alias)))))
                aliases))))

(t/deftest finds-the-cljs-aliases-in-cljsns
  (let [aliases (:cljs (sut/namespace-aliases))]
    (t/is (some #(and (= (first %) 'pprint)
                      (= (first (second %)) 'cljs.pprint))
                aliases))))

(t/deftest finds-the-clj-aliases-in-namespace-aliases
  (let [aliases (:clj (sut/namespace-aliases))]
    (t/is (some #(and (= (first %) 'clojure-string)
                      (= (first (second %)) 'clojure.string))
                aliases))))

(t/deftest finds-the-cljs-aliases-in-namespace-aliases
  (let [aliases (:cljs (sut/namespace-aliases))]
    (t/is (some #(and (= (first %) 'gstr)
                      (= (first (second %)) 'goog.string))
                aliases))))

(t/deftest sorts-by-frequencies
  (let [aliases (:clj (sut/namespace-aliases))
        _ (util/ns-form-from-string "(ns foo)")
        utils (get (rutil/filter-map #(= (first %) 'util) aliases) 'util)]
    (t/is (= (first utils) 'refactor-nrepl.util))
    (t/is (some #(= % 'refactor-nrepl.ns.helpers) utils))))
