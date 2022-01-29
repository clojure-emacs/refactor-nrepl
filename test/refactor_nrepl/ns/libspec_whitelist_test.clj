(ns refactor-nrepl.ns.libspec-whitelist-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [refactor-nrepl.ns.libspec-whitelist :as sut]
   [refactor-nrepl.ns.prune-dependencies :as prune-dependencies]))

(deftest libspec-whitelist
  (testing "Takes into account refactor-nrepls own config, and .clj-kondo/config files alike,
merging their results"
    (is (= [;; From refactor-nrepl's default config:
            "^cljsjs"
            ;; from our .clj-kondo file - symbols become quoted patterns:
            "^\\Qsample.unused.namespace\\E$"
            ;; from our .clj-kondo file - strings have 'regex' semantics so are kept as-is:
            "more.unused.namespaces*"]

           (sut/libspec-whitelist)))

    (is (every? string? (sut/libspec-whitelist))
        "Items coming from different sources all have the same class,
ensuring they will be treated homogeneously by refactor-nrepl")

    (testing "`libspec-should-never-be-pruned?` is integrated with clj-kondo logic,
effecively parsing its config into well-formed regexes"
      (are [input expected] (= expected
                               (prune-dependencies/libspec-should-never-be-pruned? {:ns input}))
        'sample.unused.namespace   true
        'Asample.unused.namespace  false
        'sample.unused.namespaceB  false
        'more.unused.namespaces    true
        'more.unused.namespacessss true
        'more.unused.namespac      false))))
