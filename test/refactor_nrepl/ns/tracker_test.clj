(ns refactor-nrepl.ns.tracker-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.ns.tracker :as sut]
   [refactor-nrepl.util :as util]))

(deftest in-refresh-dirs?
  (are [refresh-dirs file-ns expected] (= expected
                                          (sut/in-refresh-dirs? refresh-dirs
                                                                (#'sut/absolutize-dirs refresh-dirs)
                                                                file-ns))
    ;; if the refresh dirs are unset, we return `true` no matter what:
    []       "src/refactor_nrepl/ns/tracker.clj"       true

    ["src"]  "src/refactor_nrepl/ns/tracker.clj"       true
    ["src"]  "test/refactor_nrepl/ns/tracker_test.clj" false
    ["test"] "test/refactor_nrepl/ns/tracker_test.clj" true
    ["src"]  "project.clj"                             false
    ["src"]  "/"                                       false
    ["ffff"] "src/refactor_nrepl/ns/tracker.clj"       false
    ["src"]  "src/refactor_nrepl/ns/trackeeeeeer.clj"  false))

(deftest project-files-in-topo-order
  (is (seq (sut/project-files-in-topo-order false))
      "Does not throw exceptions even when specifying to not ignore errors,
i.e. it doesn't have bugs"))

(deftest build-tracker-test
  (let [refresh-dirs (#'sut/user-refresh-dirs)
        ignore-errors? true
        tracker (sut/build-tracker
                 (util/with-suppressed-errors
                   (every-pred (partial sut/in-refresh-dirs? refresh-dirs (#'sut/absolutize-dirs refresh-dirs))
                               core/clj-or-cljc-file?)
                   ignore-errors?))
        target-cljc-namespace 'com.move.cljc-test-file
        found-namespace (-> tracker
                            :clojure.tools.namespace.track/deps
                            :dependencies
                            (get target-cljc-namespace)
                            first)]
    (testing "that tracker is picking up .cljc file"
      (is (= 'clj-namespace-from.cljc-file found-namespace)))))
