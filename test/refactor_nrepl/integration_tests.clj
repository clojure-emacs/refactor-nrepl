(ns refactor-nrepl.integration-tests
  (:require [refactor-nrepl.client :refer [find-usages connect rename-symbol]]
            [refactor-nrepl.refactor]
            [refactor-nrepl.util :refer [list-project-clj-files]]
            [clojure.tools.nrepl.server :as nrserver]
            [clojure.tools.namespace.find :refer [find-clojure-sources-in-dir]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs])
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

    (load "/com/example/two")
    (load "/com/example/one")

    temp-dir))

(defn start-up-repl-server []
  (let [server (nrserver/start-server :port 7777 :handler (nrserver/default-handler #'refactor-nrepl.refactor/wrap-refactor))]
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
        result (find-usages :transport transport :ns 'com.example.two :name "foo" :clj-dir (str tmp-dir))]

    (println "tmp-dir: " tmp-dir)
    (println "result: " (map println result))

    (is (= 2 (count result)) (format "expected 2 results but got only %d" (count result)))
    (is (every? (partial re-matches #"(?s).*(one|two)\.clj.*") result) "one.clj or two.clj not found in result")

    (is (re-matches #"(?s).*\[5\].*" (first result)) "call of foo not found in ns com.example.one")

    (is (re-matches #"(?s).*\[3\].*" (second result)) "def of foo not found in ns com.example.two")

    ;; clean-up
    (.delete tmp-dir)))

(deftest test-rename-foo
  (let [tmp-dir (create-test-project)
        transport (connect :port 7777)
        new-one "(ns com.example.one
  (:require [com.example.two :as two]))

(defn bar []
  (str \"bar\" (two/baz)))
"
        new-two "(ns com.example.two)

(defn baz []
  \"foo\")
"]
    (rename-symbol :transport transport :ns 'com.example.two :name "foo" :clj-dir (str tmp-dir) :new-name "baz")

    (is (= new-one (slurp (str tmp-dir "/src/com/example/one.clj"))) "rename failed in com.example.one")

    (is (= new-two (slurp (str tmp-dir "/src/com/example/two.clj"))) "rename failed in com.example.two")
        ;; clean-up
    (.delete tmp-dir)))
