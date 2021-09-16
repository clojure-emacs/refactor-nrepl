(ns refactor-nrepl.util-test
  (:require
   [clojure.test :refer [are deftest is]]
   [refactor-nrepl.util :as sut])
  (:import
   (java.io File)))

(deftest with-additional-ex-data-test
  (try
    (sut/with-additional-ex-data [:foo :bar]
      (throw (ex-info "ok" {})))
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [foo]} (ex-data e)]
        (is (= foo :bar))))))

(deftest dir-outside-root-dir?-test
  (are [input expected] (= expected
                           (sut/dir-outside-root-dir? input))
    (File. (System/getProperty "user.dir")) false
    (File. ".")                             false
    (File. "src")                           false
    (File. "/")                             true))

(deftest data-file?-test
  (are [input expected] (= expected
                           (sut/data-file? input))
    "project.clj"       true
    "boot.clj"          true
    "data_readers.clj"  true
    "project.cljs"      false
    "boot.cljs"         false
    "data_readers.cljs" false
    "a.clj"             false))
