(ns refactor-nrepl.util.meta-test
  (:require
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.util.meta :as sut]))

(deftest distinct-test
  (let [f (fn [x _y]
            x)]
    (is (= []
           (sut/distinct f [])))
    (is (= [3 1 2]
           (sut/distinct f [3 1 2 3 1 2]))))

  (let [f (fn [x y]
            (vary-meta x merge (meta y)))
        [x :as all] (sut/distinct f
                                  [^:foo {}
                                   ^:bar {}
                                   ^:baz {}])]
    (is (= [{}] all))
    (is (= {:foo true, :bar true, :baz true}
           (meta x))))

  (let [f (fn [x y]
            (vary-meta x merge (meta y)))
        [x y :as all] (sut/distinct f
                                    [^:foo {}
                                     ^:quux {1 1}
                                     ^:bar {}
                                     ^:baz {}
                                     ^:quuz {1 1}])]
    (is (= [{} {1 1}] all))
    (is (= {:foo true, :bar true, :baz true}
           (meta x)))
    (is (= {:quux true, :quuz true}
           (meta y)))))
