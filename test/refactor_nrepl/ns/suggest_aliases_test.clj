(ns refactor-nrepl.ns.suggest-aliases-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [refactor-nrepl.ns.suggest-aliases :as sut]))

(deftest test-like-ns-name?
  (are [input expected] (= expected
                           (sut/test-like-ns-name? input))
    'test       true
    'tast       false
    'a.test     true
    'a.tast     false
    'a-test     true
    'a-ast      false
    'b.a-test   true
    'b.a-ast    false
    't-foo      true
    'bar.t-foo  true
    'te-foo     false
    'bar.te-foo false
    'unit.foo   true
    'foo.unit   true))

(deftest suggested-aliases
  (are [desc input expected] (testing input
                               (is (= expected
                                      (sut/suggested-aliases input))
                                   desc)
                               (is (every? #{input}
                                           (->> input
                                                sut/suggested-aliases
                                                vals
                                                (reduce into [])))
                                   "The values of the returned hashmap always contain exactly the given ns as-is")
                               true)
    "Returns nothing for a single-segment ns, because no alias can be derived from it"
    'a              {}

    "Returns one alias for a two-segment ns"
    'a.b            '{b [a.b]}

    "Returns two aliases for a three-segment ns"
    'a.b.c          '{c   [a.b.c]
                      b.c [a.b.c]}

    "Removes redundant bits such as `clj-` and `.core`"
    'clj-a.b.c.core '{c     [clj-a.b.c.core]
                      b.c   [clj-a.b.c.core]
                      a.b.c [clj-a.b.c.core]}))
