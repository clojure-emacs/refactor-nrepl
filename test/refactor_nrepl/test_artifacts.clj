(ns refactor-nrepl.test-artifacts
  (:require [clojure
             [edn :as edn]
             [test :refer :all]]
            [clojure.java.io :as io]
            [refactor-nrepl.artifacts :as artifacts]))

(def clojure-versions (-> (io/resource "resources/clojure-versions.edn")
                          slurp
                          edn/read-string))
(def clojure-artifacts ["clojure"])
(def clojars-artifacts (-> (io/resource "resources/clojars-artifacts.edn")
                           slurp
                           edn/read-string))

(deftest creates-a-map-of-artifacts
  (reset! artifacts/artifacts {})
  (with-redefs
    [artifacts/get-artifacts-from-clojars! (constantly clojars-artifacts)
     artifacts/get-all-clj-artifacts! (constantly clojure-artifacts)
     artifacts/get-versions! (constantly clojure-versions)]

    (is (#'artifacts/stale-cache?))

    (#'artifacts/update-artifact-cache!)

    (is (not (#'artifacts/stale-cache?)))

    (testing "Contains clojure with correct versions"
      (is (contains? @artifacts/artifacts "org.clojure/clojure"))
      (is (= (count (@artifacts/artifacts "org.clojure/clojure"))
             (count clojure-versions))))

    (testing "Contains artifacts from clojars"
      (is (contains? @artifacts/artifacts "alembic"))
      (is (some #{"0.3.1"} (get-in @artifacts/artifacts ["alembic"]))))))
