(ns test-aliases-sample
  "Supports `refactor-nrepl.ns.suggest-libspecs-test`."
  (:require
   #?(:clj [clojure.test :as test] :cljs [cljs.test :as test])))

(test/deftest foo
  (test/is (pos? (inc (rand-int 1)))))
