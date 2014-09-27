(ns refactor-nrepl.test-artifacts
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [refactor-nrepl.artifacts :as artifacts]))

(def clojure-versions (edn/read-string (slurp "test/resources/clojure-versions.edn")))
(def clojure-artifacts ["clojure"])
(def clojars-artifacts (edn/read-string (slurp "test/resources/clojars-artifacts.edn")))

(deftest creates-a-map-of-artifacts
  (with-redefs
    [artifacts/get-artifacts-from-clojars! (constantly clojars-artifacts)
     artifacts/get-all-clj-artifacts! (constantly clojure-artifacts)
     artifacts/get-versions! (constantly clojure-versions)]

    (is (#'artifacts/stale-cache?))

    (#'artifacts/update-artifact-cache!)

    (is (not (#'artifacts/stale-cache?)))

    (testing "Contains clojure with correct versions"
      (is (contains? @artifacts/artifacts "org.clojure/clojure"))
      (is (count (@artifacts/artifacts "org.clojure/clojure"))
          (count clojure-versions)))

    (testing "Contains artifacts from clojars"
      (is (contains? @artifacts/artifacts "alembic"))
      (is (some #{"0.3.1"} (get-in @artifacts/artifacts ["alembic"]))))))
