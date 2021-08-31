(ns refactor-nrepl.core-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [refactor-nrepl.config :as config]
   [refactor-nrepl.core :refer [ignore-dir-on-classpath? read-ns-form]])
  (:import
   (java.io File)))

(defmacro assert-ignored-paths
  [paths pred]
  `(doseq [p# ~paths]
     (is (~pred (ignore-dir-on-classpath? p#)))))

(deftest test-ignore-dir-on-classpath?
  (let [not-ignored ["/home/user/project/test"
                     "/home/user/project/src"
                     "/home/user/project/target/classes"]
        sometimes-ignored ["/home/user/project/checkouts/subproject"
                           "/home/user/project/resources"]
        always-ignored ["/home/user/project/target/srcdeps"]]
    (testing "predicate to ignore dirs on classpath with default config"
      (assert-ignored-paths (concat not-ignored sometimes-ignored) false?)
      (assert-ignored-paths always-ignored true?))
    (testing "predicate to ignore dirs on classpath with custom config"
      (binding [config/*config* (assoc config/*config*
                                       :ignore-paths
                                       [#".+checkouts/.+" #"resources"])]
        (assert-ignored-paths not-ignored false?)
        (assert-ignored-paths (concat always-ignored sometimes-ignored) true?)))))

(deftest test-read-ns-form
  (are [input expected] (testing input
                          (assert (-> input File. .exists))
                          (is (= expected
                                 (read-ns-form input)))
                          true)
    "test-resources/readable_file_incorrect_aliases.clj" nil
    "testproject/src/com/example/one.clj"                '(ns com.example.one
                                                            (:require [com.example.two :as two :refer [foo]]
                                                                      [com.example.four :as four]))))
