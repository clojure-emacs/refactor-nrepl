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

(deftest clj-or-cljc-file-test
  (let [clj "testproject/src/com/move/dependent_ns1.clj"
        cljs "testproject/src/com/move/dependent_ns1_cljs.cljs"
        cljc "testproject/src/com/move/cljc_test_file.cljc"]
    (is (= true
           (sut/clj-or-cljc-file? cljc)))
    (is (= true
           (sut/clj-or-cljc-file? clj)))
    (is (= false
           (sut/clj-or-cljc-file? cljs)))

    (is (= false
           (sut/clj-file? cljc)))
    (is (= true
           (sut/clj-file? clj)))
    (is (= false
           (sut/clj-file? cljs)))

    (is (= true
           (sut/cljc-file? cljc)))
    (is (= false
           (sut/cljc-file? clj)))
    (is (= false
           (sut/cljc-file? cljs)))

    (is (= false
           (sut/cljs-file? cljc)))
    (is (= false
           (sut/cljs-file? clj)))
    (is (= true
           (sut/cljs-file? cljs)))))

(deftest file-forms-test
  (is (= "(ns com.move.subdir.dependent-ns-3-cljs (:require com.move.ns-to-be-moved-cljs) (:require-macros [com.move.ns-to-be-moved :refer [macro-to-be-moved]]))"
         (sut/file-forms "testproject/src/com/move/subdir/dependent_ns_3_cljs.cljs"
                         #{:clj}))
      "cljs file parsing")

  (is (= "(ns com.move.subdir.dependent-ns-3 (:require [com.move.ns-to-be-moved :refer [fn-to-be-moved]]))"
         (sut/file-forms "testproject/src/com/move/subdir/dependent_ns_3.clj"
                         #{:clj}))
      "clj file parsing")

  (let [clj-content
        "(ns com.move.cljc-test-file (:require [clj-namespace-from.cljc-file :as foo])) (declare something-or-other)"

        cljs-content
        "(ns com.move.cljc-test-file (:require [cljs-namespace-from.cljc-file :as bar :include-macros true])) (declare something-or-other)"]

    (is (not= clj-content cljs-content)
        "Sanity check")

    (is (= clj-content
           (sut/file-forms "testproject/src/com/move/cljc_test_file.cljc"
                           #{:clj}))
        "cljc file parsing, `:clj` choice")

    (is (= cljs-content
           (sut/file-forms "testproject/src/com/move/cljc_test_file.cljc"
                           #{:cljs}))
        "cljc file parsing, `:cljs` choice")))
