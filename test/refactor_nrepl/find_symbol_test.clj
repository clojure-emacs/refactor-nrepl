(ns refactor-nrepl.find-symbol-test
  (:require
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.unreadable-files :refer [ignore-errors-str]]
   [refactor-nrepl.find.find-symbol :as sut])
  (:import
   (java.io File)))

(def from-file-path
  (-> "testproject/src/com/move/ns_to_be_moved.clj" File. .getAbsolutePath))

(deftest works
  (let [found (sut/find-symbol {:file from-file-path
                                :ns "com.move.ns-to-be-moved"
                                :line 11
                                :column 7
                                :name "fn-to-be-moved"
                                :ignore-errors ignore-errors-str
                                :dir "testproject/src"})]
    (is (seq found)
        (pr-str found))
    (is (= 4 (->> found (map :file) distinct count))
        "Finds different files with references to the queried symbol")))
