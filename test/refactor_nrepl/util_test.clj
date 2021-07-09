(ns refactor-nrepl.util-test
  (:require [clojure.test :refer [deftest is]]
            [refactor-nrepl.util :as sut]))

(deftest with-additional-ex-data-test
  (try
    (sut/with-additional-ex-data [:foo :bar]
      (throw (ex-info "ok" {})))
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [foo]} (ex-data e)]
        (is (= foo :bar))))))
