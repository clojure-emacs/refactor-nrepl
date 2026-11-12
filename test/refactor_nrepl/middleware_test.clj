(ns refactor-nrepl.middleware-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [refactor-nrepl.descriptors :as descriptors]
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

(deftest every-op-is-described
  (doseq [op-name (keys @middleware/refactor-nrepl-ops)
          :let [{:keys [doc returns]} (#'middleware/describe-op op-name)]]
    (testing op-name
      (is (not (str/blank? doc)))
      (is (seq returns) "an op that returns nothing isn't much of an op")))
  (testing "nothing is described that isn't an op"
    (is (= (set (keys descriptors/ops))
           (set (remove #(.startsWith ^String % middleware/op-prefix)
                        (keys @middleware/refactor-nrepl-ops)))))))

(deftest deprecated-ops-are-described-as-such
  (doseq [[op replacement] middleware/moved-ops
          op-name [op (str middleware/op-prefix op)]]
    (testing op-name
      (is (contains? @middleware/refactor-nrepl-ops op-name))
      (is (.startsWith ^String (:doc (#'middleware/describe-op op-name))
                       (str "Deprecated: use cider-nrepl's `" replacement "` instead. ")))))
  (testing "the bare names point at the namespaced ones"
    (is (= "Find the occurrences of a symbol in the project."
           (:doc (#'middleware/describe-op "refactor/find-symbol"))))
    (is (= "Deprecated: use `refactor/find-symbol` instead. Find the occurrences of a symbol in the project."
           (:doc (#'middleware/describe-op "find-symbol"))))))

(deftest describe-reports-what-the-ops-take-and-return
  (let [find-symbol (get-in (session/message {:op "describe" :verbose? "true"})
                            [:ops :refactor/find-symbol])]
    (is (contains? (:requires find-symbol) :file))
    (is (contains? (:optional find-symbol) :ignore-errors))
    (is (= #{:occurrence :count :skipped} (set (keys (:returns find-symbol)))))))

(deftest version-op-works-under-both-names
  (testing "bare name (legacy)"
    (let [response (session/message {:op "version"})]
      (is (string? (:version response)))
      (is (contains? (:status response) "done"))))
  (testing "namespaced name"
    (let [response (session/message {:op "refactor/version"})]
      (is (string? (:version response)))
      (is (contains? (:status response) "done")))))
