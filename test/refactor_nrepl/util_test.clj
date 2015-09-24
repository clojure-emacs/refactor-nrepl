(ns refactor-nrepl.util-test
  (:require [clojure.test :refer :all]
            [refactor-nrepl.util :refer :all]))

(deftest with-additional-ex-data-test
  (try
    (with-additional-ex-data [:foo :bar]
      (throw (ex-info "ok" {})))
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [foo]} (ex-data e)]
        (is (= foo :bar))))))
