(ns refactor-nrepl.artifacts-test
  (:require [clojure
             [edn :as edn]
             [test :refer :all]]
            [clojure.java.io :as io]
            [refactor-nrepl.artifacts :as artifacts]))

(def clojure-versions (-> (io/resource "resources/clojure-versions.edn")
                          slurp
                          edn/read-string))
(def sorted-clojure-versions
  (vector "1.7.0-alpha1"
          "1.6.0" "1.6.0-RC1" "1.6.0-beta1" "1.6.0-alpha1"
          "1.5.1" "1.5.0"))
(def clojure-artifacts ["clojure"])
(def clojars-artifacts (-> (io/resource "resources/clojars-artifacts.edn")
                           slurp
                           edn/read-string))

(deftest creates-a-map-of-artifacts
  (reset! artifacts/artifacts {})
  (with-redefs
   [artifacts/get-clojars-artifacts! (constantly clojars-artifacts)
    artifacts/get-mvn-artifacts! (constantly clojure-artifacts)
    artifacts/get-mvn-versions! (constantly clojure-versions)]

    (is (#'artifacts/stale-cache?))

    (#'artifacts/update-artifact-cache!)

    (is (not (#'artifacts/stale-cache?)))

    (testing "Contains no maven-based dependency versions fetched upfront"
      (is (contains? @artifacts/artifacts "org.clojure/clojure"))
      (is (= 0 (count (@artifacts/artifacts "org.clojure/clojure")))))

    (testing "Fetches versions of maven dependency when requested"
      (is (= (count (artifacts/artifact-versions {:artifact "org.clojure/clojure"}))
             (count clojure-versions))))

    (testing "Contains artifacts from clojars"
      (is (contains? @artifacts/artifacts "alembic"))
      (is (some #{"0.3.1"} (get-in @artifacts/artifacts ["alembic"]))))

    (testing "Sorts all the versions"
      (reset! artifacts/artifacts {"org.clojure/clojure" clojure-versions})
      (is (= sorted-clojure-versions
             (artifacts/artifact-versions {:artifact "org.clojure/clojure"}))))))
