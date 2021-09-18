(ns refactor-nrepl.ns.tracker-test
  (:require
   [refactor-nrepl.ns.tracker :as sut]
   [clojure.test :refer [are deftest]]))

(deftest in-refresh-dirs?
  (are [refresh-dirs file-ns expected] (= expected
                                          (sut/in-refresh-dirs? refresh-dirs
                                                                (#'sut/absolutize-refresh-dirs refresh-dirs)
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
