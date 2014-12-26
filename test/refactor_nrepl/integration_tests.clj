(ns refactor-nrepl.integration-tests
  (:require [refactor-nrepl.client
             :refer [find-usages connect rename-symbol remove-debug-invocations
                     find-referred var-info resolve-missing]]
            [refactor-nrepl.refactor]
            [refactor-nrepl.ns.resolve-missing]
            [refactor-nrepl.util :refer [list-project-clj-files]]
            [clojure.tools.nrepl.server :as nrserver]
            [clojure.tools.namespace.find :refer [find-clojure-sources-in-dir]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [clojure.string :as str])
  (:import (java.io File)))

(defn- create-temp-dir
  "Creates and returns a new temporary directory java.io.File."
  [name]
  (let [temp-file (File/createTempFile name nil)]
    (.delete temp-file)
    (.mkdirs temp-file)
    temp-file))

(defn create-test-project []
  (let [temp-dir (create-temp-dir "refactor-nrepl-test")
        orig-src (io/file "resources/testproject/src")]

    (fs/copy-dir orig-src temp-dir)

    temp-dir))

(defn start-up-repl-server []
  (let [server
        (nrserver/start-server
         :port 7777
         :handler (nrserver/default-handler
                    #'refactor-nrepl.refactor/wrap-refactor
                    #'refactor-nrepl.ns.resolve-missing/wrap-resolve-missing))]
    (println "server [" server "]" " started...")
    server))

(defn stop-repl-server [server]
  (println "stopping server...")
  (nrserver/stop-server server))

(defn wrap-setup-once [f]
  (let [server (start-up-repl-server)]

    (f)

    (stop-repl-server server)))

(use-fixtures :once wrap-setup-once)

(deftest test-find-two-foo
  (let [tmp-dir (create-test-project)
        transport (connect :port 7777)
        result (find-usages :transport transport :ns 'com.example.two
                            :name "foo" :clj-dir (str tmp-dir))]

    (println "tmp-dir: " tmp-dir)
    (println "result: " (map println result))

    (is (= 3 (count result)) (format "expected 2 results but got only %d" (count result)))
    (is (every? (partial re-matches #"(?s).*(one|two)\.clj.*") result) "one.clj or two.clj not found in result")

    (is (re-matches #"(?s).*\[2\].*" (first result)) "call of foo not found in ns com.example.one")

    (is (re-matches #"(?s).*\[6\].*" (second result)) "call of foo not found in ns com.example.one")

    (is (re-matches #"(?s).*\[3\].*" (last result)) "def of foo not found in ns com.example.two")

    ;; clean-up
    (.delete tmp-dir)))

(deftest test-rename-foo
  (let [tmp-dir (create-test-project)
        transport (connect :port 7777)
        new-one "(ns com.example.one
  (:require [com.example.two :as two :refer [baz]]
            [com.example.four :as four]))

(defn bar []
  (str \"bar\" (two/baz)))

(defn from-registry [k]
  (k four/registry))
"
        new-two "(ns com.example.two)

(defn ^{:doc \"some text\"} baz []
  \"foo\")
"]
    (rename-symbol :transport transport :ns 'com.example.two :name "foo"
                   :clj-dir (str tmp-dir) :new-name "baz")

    (is (= new-one (slurp (str tmp-dir "/src/com/example/one.clj")))
        "rename failed in com.example.one")

    (is (= new-two (slurp (str tmp-dir "/src/com/example/two.clj")))
        "rename failed in com.example.two")
    ;; clean-up
    (.delete tmp-dir)))

(deftest test-remove-println
  (let [tmp-dir (create-test-project)
        three-file (str tmp-dir "/src/com/example/three.clj")
        transport (connect :port 7777)
        new-three "(ns com.example.three)

(defn fn-with-println [a]
  (if a
    (str a)
    a))

(defn fn-with-let [left]
  (let [right (+ left 10)]
    (+ right left)))
"]
    (remove-debug-invocations :transport transport :file three-file)

    (is (= new-three (slurp three-file)) "remove println failed")))

(deftest test-find-referred
  (let [tmp-dir (create-test-project)
        four-file (str tmp-dir "/src/com/example/four.clj")
        transport (connect :port 7777)]

    (is (find-referred :transport transport :file four-file
                       :referred "clojure.string/split") "referred not found")

    (is (not (find-referred :transport transport :file four-file
                            :referred "clojure.string/join")) "referred found when was not used in namespace")))

(deftest test-var-info
  (let [tmp-dir (create-test-project)
        transport (connect :port 7777)
        one-file (str tmp-dir "/src/com/example/one.clj")
        two-file (str tmp-dir "/src/com/example/two.clj")
        four-file (str tmp-dir "/src/com/example/four.clj")
        result-two-foo (var-info :transport transport :file one-file :name "two/foo")
        result-core-clj (var-info :transport transport :file one-file :name "str")
        result-3rd-party (var-info :transport transport :file four-file :name "split")
        result-var-defined (var-info :transport transport :file two-file :name "foo")
        result-var-referenced (var-info :transport transport :file four-file :name "#'fn-with-println")
        result-var-def (var-info :transport transport :file four-file :name "registry")
        result-var-def-used (var-info :transport transport :file one-file :name "four/registry")]

    (is (= "com.example.two" (first result-two-foo)) "ns of var not resolved")
    (is (= "foo" (second result-two-foo)) "name of var not resolved")

    (is (= "clojure.core" (first result-core-clj)) "ns of var not resolved")
    (is (= "str" (second result-core-clj)) "name of var not resolved")

    (is (= "clojure.string" (first result-3rd-party)) "ns of var not resolved")
    (is (= "split" (second result-3rd-party)) "name of var not resolved")

    (is (= "com.example.two" (first result-var-defined)) "ns of var not resolved")
    (is (= "foo" (second result-var-defined)) "name of var not resolved")

    (is (= "com.example.three" (first result-var-referenced)) "ns of var not resolved")
    (is (= "fn-with-println" (second result-var-referenced)) "name of var not resolved")

    (is (= "com.example.four" (first result-var-def)) "ns of var not result-var-def")
    (is (= "registry" (second result-var-def)) "name of var not resolved")

    (is (= "com.example.four" (first result-var-def-used)) "ns of var not result-var-def")
    (is (= "registry" (second result-var-def-used)) "name of var not resolved")))

(deftest test-resolve-missing
  (let [transport (connect :port 7777)
        split-candidates (resolve-missing :transport transport :symbol "split")
        date-candidates (resolve-missing :transport transport :symbol "Date")]
    (is ((into #{} split-candidates) 'clojure.string))
    (is ((into #{} date-candidates) 'java.util.Date))))

(deftest find-local-arg
  (let [tmp-dir (create-test-project)
        three-file (str tmp-dir "/src/com/example/three.clj")
        transport (connect :port 7777)
        result (find-usages :transport transport :name "a" :form-index-for-local 1 :file three-file)]
    (println "tmp-dir: " tmp-dir)
    (println "result: " (map println result))

    (is (= 5 (count result)) (format "expected 5 results but got %d" (count result)))))

(deftest find-local-let
  (let [tmp-dir (create-test-project)
        three-file (str tmp-dir "/src/com/example/three.clj")
        transport (connect :port 7777)
        result (find-usages :transport transport :name "right" :form-index-for-local 2 :file three-file)]
    (println "tmp-dir: " tmp-dir)
    (println "result: " (map println result))

    (is (= 2 (count result)) (format "expected 2 results but got %d" (count result)))))
