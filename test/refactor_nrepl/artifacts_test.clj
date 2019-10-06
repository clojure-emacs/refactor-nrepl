(ns refactor-nrepl.artifacts-test
  (:require [clojure
             [edn :as edn]
             [test :refer :all]]
            [clojure.java.io :as io]
            [refactor-nrepl.artifacts :as artifacts]))

(defn- resource [filename]
  (edn/read-string (slurp (io/resource filename))))

(def clojure-versions (resource "clojure-versions.edn"))
(def aero-versions (resource "aero-versions.edn"))

(def sorted-clojure-versions
  (vector "1.7.0-alpha1"
          "1.6.0" "1.6.0-RC1" "1.6.0-beta1" "1.6.0-alpha1"
          "1.5.1" "1.5.0"))
(def clojure-artifacts ["clojure"])
(def clojars-artifacts (resource "clojars-artifacts.edn"))

(deftest creates-a-map-of-artifacts
  (reset! artifacts/artifacts {})
  (with-redefs
   [artifacts/get-clojars-artifacts! (constantly clojars-artifacts)
    artifacts/get-clojars-versions! (constantly aero-versions)
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

    (testing "Fetches version from Clojars when Maven has no results"
      (with-redefs [artifacts/get-mvn-versions! (constantly (list))]
        (is (= (set aero-versions)
               (set (artifacts/artifact-versions {:artifact "aero"}))))))

    (testing "Contains artifacts from clojars"
      (is (contains? @artifacts/artifacts "alembic"))
      (is (some #{"0.3.1"} (get-in @artifacts/artifacts ["alembic"]))))

    (testing "Sorts all the versions"
      (reset! artifacts/artifacts {"org.clojure/clojure" clojure-versions})
      (is (= sorted-clojure-versions
             (artifacts/artifact-versions {:artifact "org.clojure/clojure"}))))))

(deftest ignores-invalid-artifact-forms
  (let [bad-form "[bad/1.1 \"funky\"]"
        good-form "[foo/bar \"1.1\"]"]
    (is (nil? (#'artifacts/edn-read-or-nil bad-form)))
    (is (= 'foo/bar (first (#'artifacts/edn-read-or-nil good-form))))
    (is (= "1.1" (second (#'artifacts/edn-read-or-nil good-form))))))
