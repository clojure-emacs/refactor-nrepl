(ns refactor-nrepl.core-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [refactor-nrepl.config :as config]
   [refactor-nrepl.core :as sut])
  (:import
   (java.io File)))

(defmacro assert-ignored-paths
  [paths pred]
  `(doseq [p# ~paths]
     (is (~pred (sut/ignore-dir-on-classpath? p#)))))

(deftest test-ignore-dir-on-classpath?
  (let [not-ignored ["/home/user/project/test"
                     "/home/user/project/src"
                     "/home/user/project/target/classes"]
        sometimes-ignored ["/home/user/project/checkouts/subproject"
                           "/home/user/project/resources"]
        always-ignored ["/home/user/project/target/srcdeps"]]
    (testing "predicate to ignore dirs on classpath with default config"
      (assert-ignored-paths (concat not-ignored sometimes-ignored) false?)
      (assert-ignored-paths always-ignored true?))
    (testing "predicate to ignore dirs on classpath with custom config"
      (binding [config/*config* (assoc config/*config*
                                       :ignore-paths
                                       [#".+checkouts/.+" #"resources"])]
        (assert-ignored-paths not-ignored false?)
        (assert-ignored-paths (concat always-ignored sometimes-ignored) true?)))))

(deftest read-ns-form-test
  (let [valid-filename "testproject/src/com/example/one.clj"]
    (is (= (sut/read-ns-form valid-filename)
           (sut/read-ns-form :clj valid-filename)))
    (are [input expected] (testing input
                            (assert (-> input File. .exists))
                            (is (= expected
                                   (sut/read-ns-form input)))
                            true)
      "test-resources/readable_file_incorrect_aliases.clj" nil
      valid-filename                                       '(ns com.example.one
                                                              (:require [com.example.two :as two :refer [foo]]
                                                                        [com.example.four :as four])))))

(deftest source-files-with-clj-like-extension-test
  (let [result (sut/source-files-with-clj-like-extension true)]
    (doseq [extension [".clj" ".cljs" ".cljc"]]
      (is (pos? (count (filter (fn [^File f]
                                 (-> f .getPath (.endsWith extension)))
                               result)))))))

(deftest irrelevant-dir?
  (are [input expected] (= expected
                           (sut/irrelevant-dir? (File. input)))
    "resources"         true
    "a/resources"       true
    "a/resources/"      true
    "a/resources/b"     false

    "dev-resources"     true
    "a/dev-resources"   true
    "a/dev-resources/"  true
    "a/dev-resources/b" false

    "target"            true
    "a/target"          true
    "a/target/"         true
    "a/target/b"        false

    ;; if the string ".gitlibs" is contained, it's always irrelevant:
    ".gitlibs"          true
    "a/.gitlibs"        true
    "a/.gitlibs/"       true
    "a/.gitlibs/b"      true))

(deftest read-ns-form-with-meta
  (testing "`:as-alias` directives are kept"
    (is (= '(ns as-alias (:require [foo :as-alias f]))
           (sut/read-ns-form-with-meta "test-resources/as_alias.clj")))))

(deftest extract-ns-meta
  (testing "namespace metadata and attr-map are extracted and merged together"
    (let [ns-meta (sut/extract-ns-meta (slurp "test-resources/ns_with_meta_and_attr_map.clj"))]
      (is (= {:a   1
              :b   2
              :bar true
              :foo true}
             (:top-level-meta ns-meta)))
      (is (= {:c 3, :d 4} (:attr-map ns-meta))))))
