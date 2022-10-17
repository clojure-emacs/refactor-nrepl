(ns refactor-nrepl.ns.libspec-allowlist-test
  {:clj-kondo/config '{:linters {:unused-namespace {:exclude [from-ns-attr-map
                                                              "from-ns-attr-map.re*"]}}}}
  (:require
   [clojure.test :refer [are deftest is testing]]
   [refactor-nrepl.ns.libspec-allowlist :as sut]
   [refactor-nrepl.ns.prune-dependencies :as prune-dependencies]
   [refactor-nrepl.ns.ns-parser :refer [parse-ns]]))

(def this-ns (parse-ns "test/refactor_nrepl/ns/libspec_allowlist_test.clj"))

(deftest libspec-allowlist-test
  (testing "Takes into account refactor-nrepls own config, and .clj-kondo/config files alike,
merging their results"
    (is (= [;; From refactor-nrepl's default config:
            "^cljsjs"
            ;; from our .clj-kondo file - symbols become quoted patterns:
            "^\\Qsample.unused.namespace\\E$"
            ;; from our .clj-kondo file - strings have 'regex' semantics so are kept as-is:
            "more.unused.namespaces*"
            ;; from our .clj-konfo file, namespace local configuration
            "^\\Qfrom-config-in-ns\\E$"
            "^from-config-in-ns.re*"
            ;; from attr-map of this ns
            "^\\Qfrom-ns-attr-map\\E$"
            "from-ns-attr-map.re*"]

           (sut/libspec-allowlist this-ns)))

    (is (every? string? (sut/libspec-allowlist this-ns))
        "Items coming from different sources all have the same class,
ensuring they will be treated homogeneously by refactor-nrepl")

    (testing "`libspec-should-never-be-pruned?` is integrated with clj-kondo logic,
effectively parsing its config into well-formed regexes"
      (are [input expected] (= expected
                               (prune-dependencies/libspec-should-never-be-pruned? this-ns {:ns input}))
        'sample.unused.namespace   true
        'Asample.unused.namespace  false
        'sample.unused.namespaceB  false
        'more.unused.namespaces    true
        'more.unused.namespacessss true
        'more.unused.namespac      false
        'from-config-in-ns         true
        'from-config-in-ns.core    false
        'from-config-in-ns.re      true
        'from-config-in-ns.re.f    true
        'Bfrom-config-in-ns.re     false
        'from-ns-attr-map          true
        'Efrom-ns-attr-map         false
        'from-ns-attr-map.re       true
        'from-ns-attr-map.re.f     true))

    (testing "Always returns a sequence, memoized or not"
      (is (seq (sut/with-memoized-libspec-allowlist
                 (sut/libspec-allowlist this-ns))))
      (is (seq (sut/libspec-allowlist this-ns))))))

(deftest maybe-unwrap-quote
  (testing "unwraps object if it is quoted, returns it unchanged otherwise"
    (are [input expected] (= expected (sut/maybe-unwrap-quote input))
      ''{:a 1} {:a 1}
      {:b 2}   {:b 2}
      nil      nil
      ''{}     {}
      {}       {})))
