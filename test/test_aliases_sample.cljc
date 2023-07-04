(ns test-aliases-sample
  "Supports `refactor-nrepl.ns.suggest-libspecs-test`."
  {:clj-kondo/config {:linters {:unused-namespace {:level :off}}}}
  (:require
   #?(:clj [donkey.jvm :as donkey] :cljs [donkeyscript :as donkey])
   #?(:clj [clojure.test :as test] :cljs [cljs.test :as test])))

(test/deftest foo
  (test/is (pos? (inc (int (rand-int 1))))))
