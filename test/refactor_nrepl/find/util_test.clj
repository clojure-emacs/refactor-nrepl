(ns refactor-nrepl.find.util-test
  (:require [clojure.test :refer :all]
            [refactor-nrepl.find.util :as sut]))

(deftest divide-by
  (are [input n expected] (= expected
                             (sut/divide-by n input))
    []      1 '()
    [1]     1 '((1))
    [1 1]   1 '((1 1))
    [1 1 1] 1 '((1 1 1))

    []      2 '()
    [1]     2 '((1))
    [1 1]   2 '((1) (1))
    [1 1 1] 2 '((1 1) (1))

    []      3 '()
    [1]     3 '((1))
    [1 1]   3 '((1) (1))
    [1 1 1] 3 '((1) (1) (1))))
