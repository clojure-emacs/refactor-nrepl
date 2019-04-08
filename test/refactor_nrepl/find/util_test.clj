(ns refactor-nrepl.find.util-test
  (:require [clojure.test :refer :all]
            [refactor-nrepl.find.util :as sut]))

(deftest slice
  (are [input n expected] (= expected
                             (sut/slice n input))
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

(deftest distribute-evenly-by
  (testing "Basic behavior"
    (are [i n e] (= e
                    (sut/distribute-evenly-by {:n n
                                               :f identity}
                                              i))

      []                                1 []
      []                                2 []

      [1 1 2 2]                         1 [1 1 2 2]
      [1 1 2 2]                         2 [1 2, 1 2]
      [1 1 2 2]                         4 [1, 1, 2, 2]

      [1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4] 1 [1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4]
      [1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4] 2 [1 1 2 2 3 3 4 4, 1 1 2 2 3 3 4 4]
      [1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4] 4 [1 2 3 4, 1 2 3 4, 1 2 3 4, 1 2 3 4]

      [1 1 1 2 2 2 3 3 3]               3 '[1 2 3, 1 2 3, 1 2 3]))

  (testing "No items are ever lost or modified: they are only sorted"
    (doseq [input-index (range 100)
            n (range 1 101)
            :let [input (range input-index)
                  output (sut/distribute-evenly-by {:n n
                                                    :f identity}
                                                   input)]]
      (is (= (->> output sort)
             input)))))

(deftest workload-partitioning
  (testing "`distribute-evenly-by` works well in conjunction with `slice`"
    (are [input n expected] (= expected
                               (->> input
                                    (sut/distribute-evenly-by {:n n
                                                               :f identity})
                                    (sut/slice n)))
      []                                4 []
      [1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4] 4 '[(1 2 3 4) (1 2 3 4) (1 2 3 4) (1 2 3 4)])

    (doseq [_ (range 100)]
      (= '[(1 2 3 4) (1 2 3 4) (1 2 3 4) (1 2 3 4)]
         (->> [1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4]
              (shuffle)
              (sut/distribute-evenly-by {:n 4
                                         :f identity})
              (sut/slice 4))))))
