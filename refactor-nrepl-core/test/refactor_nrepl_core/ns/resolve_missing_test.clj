(ns refactor-nrepl-core.ns.resolve-missing-test
  (:require [refactor-nrepl-core.ns.resolve-missing :refer :all]
            [clojure.test :refer :all]))

(defrecord Foo [])
(deftype Bar [])
(definterface Baz)

(deftest test-resolve-missing
  (let [split-res (resolve-missing "split")
        date-res (resolve-missing "Date")
        foo-res (resolve-missing "Foo")
        bar-res (resolve-missing "Bar")
        baz-res (resolve-missing "Baz")
        split-type (second (first (filter #(= (first %) 'clojure.string) split-res)))
        date-type (second (first (filter #(= (first %) 'java.util.Date) date-res)))
        foo-type (second (first (filter #(= (first %) 'refactor_nrepl_core.ns.resolve_missing_test.Foo)
                                        foo-res)))
        bar-type (second (first (filter #(= (first %) 'refactor_nrepl_core.ns.resolve_missing_test.Bar) bar-res)))
        baz-type (second (first (filter #(= (first %) 'refactor_nrepl_core.ns.resolve_missing_test.Baz) baz-res)))]

    (is ((set (map first split-res)) 'clojure.string))
    (is ((set (map first date-res)) 'java.util.Date))
    (is ((set (map first foo-res)) 'refactor_nrepl_core.ns.resolve_missing_test.Foo))
    (is ((set (map first bar-res)) 'refactor_nrepl_core.ns.resolve_missing_test.Bar))
    (is ((set (map first baz-res)) 'refactor_nrepl_core.ns.resolve_missing_test.Baz))
    (is (= date-type :class))
    (is (= foo-type :type))
    (is (= bar-type :type))
    (is (= baz-type :class))
    (is (= split-type :ns))))
