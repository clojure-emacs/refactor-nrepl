(ns refactor-nrepl.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [refactor-nrepl
             [analyzer :as analyzer]
             [config :as sut]]))

(deftest error-from-ast-is-sent-to-user-with-debug-setting
  (with-redefs [refactor-nrepl.analyzer/cachable-ast (fn [& _] (IllegalThreadStateException. "NO!"))]
    (testing "Throws pretty error with :debug false"
      (sut/with-config {:debug false}
        (is (thrown? IllegalStateException (analyzer/ns-ast "my-file")))))
    (testing "Throws original exception with :debug true"
      (sut/with-config {:debug true}
        (is (thrown? IllegalThreadStateException (analyzer/ns-ast "my-file")))))))
