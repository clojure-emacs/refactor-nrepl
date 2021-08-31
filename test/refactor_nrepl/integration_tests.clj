(ns refactor-nrepl.integration-tests
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [nrepl.server :as nrepl]
            [refactor-nrepl middleware
             [analyzer :as analyzer]
             [client :refer [connect find-unbound find-usages resolve-missing version]]
             [core :as core]]))

(defn start-up-repl-server []
  (let [server
        (nrepl/start-server
         :bind "localhost"
         :port 7777
         :handler (nrepl/default-handler
                   #'refactor-nrepl.middleware/wrap-refactor))]
    server))

(defn stop-repl-server [server]
  (nrepl/stop-server server))

(defn wrap-setup-once [f]
  (let [server (start-up-repl-server)]

    (f)

    (stop-repl-server server)))

(use-fixtures :once wrap-setup-once)

(def test-project-dir "testproject/src")

(deftest test-find-two-foo
  (let [transport (connect :port 7777)
        response (find-usages :transport transport :ns 'com.example.two
                              :file (str test-project-dir "/com/example/one.clj")
                              :line 6 :column 19
                              :name "foo" :dir test-project-dir)
        result (remove keyword? response)]
    (testing (pr-str result)
      (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))
      (is (every? (partial re-matches #"(?s).*(one|two)\.clj.*") result) "one.clj or two.clj not found in result")

      (is (some (partial re-matches #"(?s).*one.clj \[2\].*") result) "call of foo not found in ns com.example.one")

      (is (some (partial re-matches #"(?s).*one.clj \[6\].*") result) "call of foo not found in ns com.example.one")

      (is (some (partial re-matches #"(?s).*two.clj \[3\].*") result) "def of foo not found in ns com.example.two"))))

(defn ns-ast-throw-error-for-five [^String content]
  (if (.contains content "com.example.five")
    (throw (IllegalThreadStateException. "Expected!"))
    (#'analyzer/cachable-ast content)))

(deftest test-find-two-foo-errors-ignored
  (with-redefs [analyzer/ns-ast ns-ast-throw-error-for-five]
    (let [transport (connect :port 7777)
          response (find-usages :transport transport :ns 'com.example.two
                                :file (str test-project-dir "/com/example/one.clj")
                                :line 6 :column 19
                                :name "foo" :dir test-project-dir)
          result (remove keyword? response)]

      (testing (pr-str result)

        (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))
        (is (every? (partial re-matches #"(?s).*(one|two)\.clj.*") result) "one.clj or two.clj not found in result")

        (is (some (partial re-matches #"(?s).*one.clj \[2\].*") result) "call of foo not found in ns com.example.one")

        (is (some (partial re-matches #"(?s).*one.clj \[6\].*") result) "call of foo not found in ns com.example.one")

        (is (some (partial re-matches #"(?s).*two.clj \[3\].*") result) "def of foo not found in ns com.example.two")))))

(deftest test-shouldnt-find-str-in-assert
  (let [transport (connect :port 7777)
        response (find-usages :transport transport :ns 'clojure.core
                              :file (str test-project-dir "/com/example/macros.clj")
                              :line 8 :column 4
                              :name "str" :dir test-project-dir)
        result (remove keyword? response)]

    (is (not-any? #(.contains ^String % "(assert (> x 0)") result) "`assert` found when searching for `clojure.core/str`")))

(deftest test-shouldnt-find-expanded-fn-in-place-of-macro
  (let [transport (connect :port 7777)
        response (find-usages :transport transport :ns 'com.example.macros
                              :file (str test-project-dir "/com/example/macros.clj")
                              :line 7 :column 11
                              :name "str-nicely" :dir test-project-dir)
        result (remove keyword? response)]
    ;;(clojure.pprint/pprint result)

    (is (not-any? #(.contains ^String % "(nicely x)") result) "`str-nicely` found at macro call site")))

(deftest test-find-fn-in-similarly-named-ns
  (let [transport (connect :port 7777)
        response (find-usages :transport transport :ns 'com.example.three
                              :file (str test-project-dir "/com/example/four.clj")
                              :line 11 :column 3
                              :name "thre" :dir test-project-dir)
        result (remove keyword? response)]
    (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))))

(deftest test-find-fn-in-dashed-ns
  (let [transport (connect :port 7777)
        response (find-usages :transport transport :ns 'com.example.twenty-four
                              :file (str test-project-dir "/com/example/four.clj")
                              :line 14 :column 4
                              :name "stuff" :dir test-project-dir)
        result (remove keyword? response)]

    (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))))

(deftest test-find-dashed-fn
  (let [transport (connect :port 7777)
        response (find-usages :transport transport :ns 'com.example.twenty-four
                              :file (str test-project-dir "/com/example/four.clj")
                              :line 16 :column 4
                              :name "more-stuff" :dir test-project-dir)
        result (remove keyword? response)]
    (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))))

(defrecord Foo [])
(deftype Bar [])
(definterface Baz)

(deftest test-resolve-missing
  (let [transport (connect :port 7777)
        split-res (resolve-missing :transport transport :symbol "split")
        date-res (resolve-missing :transport transport :symbol "Date")
        foo-res (resolve-missing :transport transport :symbol "Foo")
        bar-res (resolve-missing :transport transport :symbol "Bar")
        baz-res (resolve-missing :transport transport :symbol "Baz")
        pattern-res (resolve-missing :transport transport :symbol "Pattern/quote")
        pattern-type (:type (first (filter #(= (:name %) 'java.util.regex.Pattern) pattern-res)))
        split-type (:type (first (filter #(= (:name %)  'clojure.string) split-res)))
        date-type (:type (first (filter #(= (:name %) 'java.util.Date) date-res)))
        foo-type (:type (first (filter #(= (:name %) 'refactor_nrepl.integration_tests.Foo)
                                       foo-res)))
        bar-type (:type (first (filter #(= (:name %) 'refactor_nrepl.integration_tests.Bar) bar-res)))
        baz-type (:type (first (filter #(= (:name %) 'refactor_nrepl.integration_tests.Baz) baz-res)))]
    (is ((set (map :name split-res)) 'clojure.string)
        (pr-str [(set (map :name split-res))
                 split-res]))
    (is ((set (map :name pattern-res)) 'java.util.regex.Pattern))
    (is ((set (map :name date-res)) 'java.util.Date))
    (is ((set (map :name foo-res)) 'refactor_nrepl.integration_tests.Foo))
    (is ((set (map :name bar-res)) 'refactor_nrepl.integration_tests.Bar))
    (is ((set (map :name baz-res)) 'refactor_nrepl.integration_tests.Baz))
    (is (= date-type :class))
    (is (= pattern-type :class))
    (is (= foo-type :type))
    (is (= bar-type :type))
    (is (= baz-type :class))
    (is (= split-type :ns)
        (pr-str (filter #(= (:name %)  'clojure.string) split-res)))))

(deftest find-local-arg
  (let [three-file (str test-project-dir "/com/example/three.clj")
        transport (connect :port 7777)
        response (find-usages :transport transport :name "a" :file three-file :line 3 :column 24)
        result (remove keyword? response)]
    (is (= 5 (count result)) (format "expected 5 results but got %d" (count result)))))

(deftest find-local-let
  (let [three-file (str test-project-dir "/com/example/three.clj")
        transport (connect :port 7777)
        response (find-usages :transport transport :name "right" :file three-file :line 12 :column 12)
        result (remove keyword? response)]
    (is (= 2 (count result)) (format "expected 2 results but got %d" (count result)))))

(deftest find-local-in-optmap-default
  (let [five-file (str test-project-dir "/com/example/five.clj")
        transport (connect :port 7777)
        response (find-usages :transport transport :name "foo" :file five-file :line 47 :column 10)
        result (remove keyword? response)]
    (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))))

(deftest find-local-in-optmap-default-linebreaks
  (let [five-file (str test-project-dir "/com/example/five.clj")
        transport (connect :port 7777)
        response (find-usages :transport transport :name "foo" :file five-file :line 50 :column 12)
        result (remove keyword? response)]
    (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))))

(deftest find-local-in-optmap-default-in-let
  (let [five-file (str test-project-dir "/com/example/five.clj")
        transport (connect :port 7777)
        response (find-usages :transport transport :name "foo" :file five-file :line 60 :column 12)
        result (remove keyword? response)]
    (is (= 3 (count result)) (format "expected 3 results but got %d" (count result)))))

(core/with-clojure-version->= {:major 1 :minor 9}
  (deftest find-local-in-namespaced-destructuring
    (let [five-file (str test-project-dir "/com/example/five.clj")
          transport (connect :port 7777)
          response (find-usages :transport transport :name "foo" :file five-file :line 67 :column 16)
          result (remove keyword? response)]
      (is (= 2 (count result)) (format "expected 3 results but got %d" (count result))))))

(deftest test-find-used-locals
  (let [five-file (str test-project-dir "/com/example/five.clj")
        transport (connect :port 7777)]
    (is (= (find-unbound :transport transport :file five-file :line 13 :column 6)
           '(s)))
    (is (= (find-unbound :transport transport :file five-file :line 14 :column 13)
           '(s sep)))

    (is (= (find-unbound :transport transport :file five-file :line 21 :column 16)
           '(p)))
    (is (= (find-unbound :transport transport :file five-file :line 28 :column 8)
           '(sep strings)))

    (is (= (find-unbound :transport transport :file five-file :line 35 :column 8)
           '(name)))

    (is (= (find-unbound :transport transport :file five-file :line 38 :column 5)
           '(n)))
    (is (= (find-unbound :transport transport :file five-file :line 42 :column 4)
           '(x y z a b c)))))

(deftest find-unbound-fails-on-cljs
  (let [cljs-file (str test-project-dir "/tmp/src/com/example/file.cljs")
        transport (connect :port 7777)]
    (is (:error (find-unbound :transport transport :file cljs-file
                              :line 12 :column 6)))))

(deftest test-version
  (is (= (str (core/version))
         (version :transport (connect :port 7777)))))
