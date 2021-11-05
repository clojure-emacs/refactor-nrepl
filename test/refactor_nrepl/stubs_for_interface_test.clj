(ns refactor-nrepl.stubs-for-interface-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [refactor-nrepl.stubs-for-interface :as sut]))

(defprotocol AProtocol
  (regular-fn [arg1] "With a docstring")
  (overloaded-fn [arg1] [arg1 arg2] [arg1 arg2 arg3] "With a docstring"))

(deftest stubs-for-Runnable
  (let [run (first (sut/stubs-for-interface {:interface "java.lang.Runnable"}))]
    (is (= (:parameter-list run) "[]"))
    (is (= (:name run) "run"))))

(deftest stubs-for-AProtocol
  (let [my-protocol (sut/stubs-for-interface
                     {:interface "refactor-nrepl.stubs-for-interface-test/AProtocol"})
        overloaded-fns (filter #(= (:name %) "overloaded-fn") my-protocol)]
    (is (= (count my-protocol) 4))
    (is (= (count overloaded-fns) 3))
    (is (some #(= (:parameter-list %) "[this]") overloaded-fns))
    (is (some #(= (:parameter-list %) "[this arg1]") overloaded-fns))
    (is (some #(= (:parameter-list %) "[this arg1 arg2]") overloaded-fns))))

(deftest stubs-for-MyInterface
  (let [my-interface (sut/stubs-for-interface
                      {:interface "refactor.nrepl.MyInterface"})]
    (is (= (count my-interface) 2))
    (testing "Removal of java.lang prefix"
      (is (some #(= (:parameter-list %) "[^String arg]") my-interface)))
    (is (some #(= (:parameter-list %) "[^java.util.List arg]") my-interface))))
