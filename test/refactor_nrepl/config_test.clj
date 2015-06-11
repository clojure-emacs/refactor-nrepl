(ns refactor-nrepl.config-test
  (:require [refactor-nrepl
             [analyzer :as analyzer]
             [config :refer :all]]
            [clojure.test :refer :all]))

(deftest throws-on-bad-config
  (is (thrown? IllegalArgumentException (configure "invalid")))
  (is (thrown? IllegalArgumentException (configure {:invalid-key 1}))))

(deftest error-from-ast-is-sent-to-user-with-debug-setting
  (with-redefs [refactor-nrepl.analyzer/cachable-ast (fn [& _] (throw (IllegalThreadStateException. "NO!")))]
    (testing "Throws pretty error with :debug false"
      (is (thrown? IllegalStateException (analyzer/ns-ast "my-file"))))
    (testing "Throws original exception with :debug true"
      (set-opt! :debug true)
      (is (thrown? IllegalThreadStateException (analyzer/ns-ast "my-file"))))))
