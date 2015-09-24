(ns refactor-nrepl.ns.namespace-aliases-test
  (:require [clojure.test :as t]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.ns.namespace-aliases :as sut]
            [refactor-nrepl.util :as util]))

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
        _ (core/ns-form-from-string "(ns foo)")
        utils (get (util/filter-map #(= (first %) 'util) aliases) 'util)]
    (t/is (= (first utils) 'refactor-nrepl.util))))

(t/deftest libspecs-are-cached
  (sut/namespace-aliases)
  (with-redefs [refactor-nrepl.ns.namespace-aliases/put-cached-libspec
                (fn [& _] (throw (ex-info "Cache miss!" {})))]
    (t/is (sut/namespace-aliases)))
  (reset! @#'sut/cache {})
  (with-redefs [refactor-nrepl.ns.namespace-aliases/put-cached-libspec
                (fn [& _] (throw (Exception. "Expected!")))]
    (t/is (thrown-with-msg? Exception #"Expected!"
                            (sut/namespace-aliases)))))
