(ns refactor-nrepl.ns.namespace-aliases-test
  (:require [clojure.test :refer [deftest is]]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.ns.libspecs :as sut]
            [refactor-nrepl.util :as util]
            [refactor-nrepl.unreadable-files :refer [ignore-errors?]]))

(defn finds [selector alias libspec]
  (let [aliases (selector (sut/namespace-aliases ignore-errors?))]
    (some (fn [[k vs]]
            (and (= k alias)
                 (some #{libspec} vs)))
          aliases)))

(deftest finds-the-aliases-in-this-ns
  (is (finds :clj 'sut 'refactor-nrepl.ns.libspecs)))

(deftest finds-the-cljs-aliases-in-cljsns
  (is (finds :cljs 'pprint 'cljs.pprint)))

(deftest finds-the-clj-aliases-in-namespace-aliases
  (is (finds :clj 'clojure-string 'clojure.string)))

(deftest finds-the-cljs-aliases-in-namespace-aliases
  (is (finds :cljs 'gstr 'goog.string)))

(deftest sorts-by-frequencies
  (let [aliases (:clj (sut/namespace-aliases ignore-errors?))
        _ (core/ns-form-from-string "(ns foo)")
        utils (get (util/filter-map #(= (first %) 'util) aliases) 'util)]
    (is (= (first utils) 'refactor-nrepl.util))))

(deftest libspecs-are-cached
  (sut/namespace-aliases ignore-errors?)
  (with-redefs [refactor-nrepl.ns.libspecs/put-cached-libspec
                (fn [& _] (throw (ex-info "Cache miss!" {})))]
    (is (sut/namespace-aliases ignore-errors?)))
  (reset! @#'sut/cache {})
  (with-redefs [refactor-nrepl.ns.libspecs/put-cached-libspec
                (fn [& _] (throw (Exception. "Expected!")))]
    (is (thrown-with-msg? Exception #"Expected!"
                          (sut/namespace-aliases false)))))
