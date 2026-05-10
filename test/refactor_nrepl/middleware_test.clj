(ns refactor-nrepl.middleware-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [refactor-nrepl.middleware :as middleware]
   [refactor-nrepl.test-session :as session]))

(use-fixtures :each session/session-fixture)

(deftest ops-are-registered-under-bare-and-namespaced-names
  (let [registered (set (keys @middleware/refactor-nrepl-ops))]
    (testing "every bare op has a refactor/<op> counterpart"
      (doseq [op registered
              :when (not (.startsWith ^String op middleware/op-prefix))]
        (is (contains? registered (str middleware/op-prefix op))
            (str op " is missing its " middleware/op-prefix " counterpart"))))
    (testing "namespaced names route to the same handler as bare names"
      (doseq [op registered
              :when (not (.startsWith ^String op middleware/op-prefix))]
        (is (identical? (get @middleware/refactor-nrepl-ops op)
                        (get @middleware/refactor-nrepl-ops (str middleware/op-prefix op))))))))

(deftest version-op-works-under-both-names
  (testing "bare name (legacy)"
    (let [response (session/message {:op "version"})]
      (is (string? (:version response)))
      (is (contains? (:status response) "done"))))
  (testing "namespaced name"
    (let [response (session/message {:op "refactor/version"})]
      (is (string? (:version response)))
      (is (contains? (:status response) "done")))))
