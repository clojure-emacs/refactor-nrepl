(ns refactor-nrepl.artifacts-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [refactor-nrepl.artifacts :as artifacts])
  (:import
   (java.io File)
   (java.util.zip GZIPOutputStream)))

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

;; The tests below run against canned responses. The ones that talk to the
;; actual services only run with `make network-test`, as Maven Central and
;; Clojars having a bad day shouldn't fail the build.

(def ^:private network-tests?
  (some? (System/getenv "NETWORK_TESTS")))

(deftest get-mvn-artifacts!-test
  (testing "Extracts the artifact ids from the search results"
    (let [requested (atom nil)]
      (with-redefs [artifacts/http-get
                    (fn [url]
                      (reset! requested url)
                      "{\"response\": {\"docs\": [{\"g\": \"org.clojure\", \"a\": \"clojure\"},
                                                {\"g\": \"org.clojure\", \"a\": \"core.async\"},
                                                {\"g\": \"org.clojure\"}]}}")]
        (is (= ["clojure" "core.async"] (#'artifacts/get-mvn-artifacts! "org.clojure")))
        (is (re-find #"q=g:%22org\.clojure%22" @requested))
        (is (re-find #"rows=200&" @requested)
            "the search API doesn't serve more than 200 rows"))))
  (testing "Comes back empty when Maven Central can't be reached"
    (with-redefs [artifacts/http-get (constantly nil)]
      (is (empty? (#'artifacts/get-mvn-artifacts! "org.clojure"))))))

(deftest get-mvn-versions!-test
  (testing "Extracts the versions from the search results"
    (let [requested (atom nil)]
      (with-redefs [artifacts/http-get
                    (fn [url]
                      (reset! requested url)
                      "{\"response\": {\"docs\": [{\"v\": \"1.12.0\"}, {\"v\": \"1.11.1\"}, {}]}}")]
        (is (= ["1.12.0" "1.11.1"] (#'artifacts/get-mvn-versions! "org.clojure/clojure")))
        (is (re-find #"g:%22org\.clojure%22\+AND\+a:%22clojure%22" @requested)))))
  (testing "Comes back empty when Maven Central can't be reached"
    (with-redefs [artifacts/http-get (constantly nil)]
      (is (empty? (#'artifacts/get-mvn-versions! "org.clojure/clojure"))))))

(deftest get-clojars-versions!-test
  (testing "Extracts the versions from the artifact's details"
    (let [requested (atom nil)]
      (with-redefs [artifacts/http-get
                    (fn [url]
                      (reset! requested url)
                      "{\"recent_versions\": [{\"version\": \"3.16.0\", \"downloads\": 1},
                                            {\"version\": \"3.15.0\", \"downloads\": 2}]}")]
        (is (= ["3.16.0" "3.15.0"] (artifacts/get-clojars-versions! "refactor-nrepl/refactor-nrepl")))
        (is (= "https://clojars.org/api/artifacts/refactor-nrepl/refactor-nrepl" @requested)))))
  (testing "Has nothing to say when Clojars can't be reached"
    (with-redefs [artifacts/http-get (constantly nil)]
      (is (nil? (artifacts/get-clojars-versions! "refactor-nrepl/refactor-nrepl"))))))

(deftest get-clojars-artifacts!-test
  (testing "Reads the gzipped list of jars, skipping the lines it can't read"
    (let [file (File/createTempFile "all-jars" ".clj.gz")]
      (try
        (with-open [out (io/writer (GZIPOutputStream. (io/output-stream file)))]
          (.write out "[alembic \"0.3.1\"]\n[bad/1.1 \"funky\"]\n[aero \"1.1.6\"]\n"))
        (with-redefs [artifacts/clojars-all-jars-url (str (.toURL (.toURI file)))]
          (is (= '[[alembic "0.3.1"] [aero "1.1.6"]]
                 (#'artifacts/get-clojars-artifacts!))))
        (finally
          (.delete file)))))
  (testing "Comes back empty when Clojars can't be reached"
    (with-redefs [artifacts/clojars-all-jars-url "file:///no/such/all-jars.clj.gz"]
      (is (empty? (#'artifacts/get-clojars-artifacts!))))))

(deftest live-maven-central-test
  (when network-tests?
    (is (> (count (#'artifacts/get-mvn-artifacts! "org.clojure")) 10))
    (is (> (count (#'artifacts/get-mvn-versions! "org.clojure/clojure")) 20))))

(deftest live-clojars-test
  (when network-tests?
    (is (> (count (#'artifacts/get-clojars-artifacts!)) 1000))
    (is (> (count (artifacts/get-clojars-versions! "refactor-nrepl/refactor-nrepl")) 30))))

(deftest creates-a-map-of-artifacts
  (reset! artifacts/artifacts {})
  (with-redefs [artifacts/get-clojars-artifacts! (constantly clojars-artifacts)
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
