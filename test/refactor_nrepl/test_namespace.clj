(ns refactor-nrepl.test-namespace
  (:require [clojure.test :refer :all]
            [refactor-nrepl.ns
             [clean-ns :refer [clean-ns]]
             [helpers :refer [get-ns-component read-ns-form]]]
            [refactor-nrepl.ns.pprint :refer [pprint-ns]]
            [clojure.string :as str])
  (:import java.io.File))

(def ns1 (.getAbsolutePath (File. "test/resources/ns1.clj")))
(def ns1-cleaned (read-ns-form (.getAbsolutePath (File. "test/resources/ns1_cleaned.clj"))))
(def ns2 (.getAbsolutePath (File. "test/resources/ns2.clj")))
(def ns2-cleaned (read-ns-form (.getAbsolutePath (File. "test/resources/ns2_cleaned.clj"))))
(def ns2-meta (.getAbsolutePath (File. "test/resources/ns2_meta.clj")))
(def ns-with-exclude (read-ns-form (.getAbsolutePath (File. "test/resources/ns_with_exclude.clj"))))
(def ns-with-unused-deps (.getAbsolutePath (File. "test/resources/unused_deps.clj")))
(def ns-without-unused-deps (read-ns-form
                             (.getAbsolutePath (File. "test/resources/unused_removed.clj"))))
(def cljs-file (.getAbsolutePath (File. "test/resources/file.cljs")))

(deftest combines-requires
  (let [requires (get-ns-component (clean-ns ns2) :require)
        combined-requires (get-ns-component ns2-cleaned :require)]
    (is (= combined-requires requires))))

(deftest meta-preserved
  (let [cleaned (pprint-ns (clean-ns ns2-meta))
        expected-cleaned (str/replace (slurp "test/resources/ns2_cleaned_meta.clj") "ns2-cleaned-meta" "ns2-meta")]
    (is (= expected-cleaned cleaned))))

(deftest preserves-removed-use
  (let [requires (get-ns-component (clean-ns ns2) :use)
        combined-requires (get-ns-component ns2-cleaned :require)]
    (is (reduce
         #(or %1 (= %2 '[clojure
                         [edn :refer :all :rename {read-string rs read rd}]
                         [instant :refer :all]
                         [pprint :refer [cl-format fresh-line get-pretty-writer]]
                         [string :refer :all :rename {replace foo reverse bar}]
                         [test :refer :all]]))
         false
         (tree-seq sequential? identity combined-requires)))))

(deftest removes-use-with-rename-clause
  (let [requires (get-ns-component (clean-ns ns2) :use)
        combined-requires (get-ns-component ns2-cleaned :require)]
    (is (reduce
         #(or %1 (= %2 '[edn :refer :all :rename {read-string rs
                                                  read rd}]))
         false
         (tree-seq sequential? identity combined-requires)))))

(deftest test-sort-and-prefix-favoring
  (let [requires (get-ns-component (clean-ns ns1) :require)
        imports (get-ns-component (clean-ns ns1) :import)
        sorted-requires (get-ns-component ns1-cleaned :require)
        sorted-imports (get-ns-component ns1-cleaned :import)]
    (is (= sorted-requires requires))
    (is (= sorted-imports imports))))

(deftest throws-exceptions-for-unexpected-elements
  (is (thrown? IllegalArgumentException
               (clean-ns ns-with-exclude))))

(deftest throws-on-malformed-ns
  (is (thrown? IllegalArgumentException
               (read-ns-form (.getAbsolutePath
                              (File. "test/resources/clojars-artifacts.edn"))))))

(deftest preserves-other-elements
  (let [actual (pprint-ns (clean-ns ns1))
        actual-form (read-string actual)]
    (is (.contains actual "^{:doc \"This is a docstring for the ns\", :author \"Winnie the pooh\"}"))
    (is (not= "This is a docstring for the ns" (nth actual-form 2)))
    (is (not= {:author "Winnie the pooh"} (nth actual-form 3)))))

(deftest removes-use
  (let [use-clause (get-ns-component ns1-cleaned :use)]
    (is (nil? use-clause))))

(deftest combines-multiple-refers
  (let [requires (clean-ns ns2)
        refers '[cl-format fresh-line get-pretty-writer]]
    (is (reduce
         #(or %1 (= %2 refers))
         false
         (tree-seq sequential? identity requires)))))

(deftest combines-multiple-refers-to-all
  (let [requires (clean-ns ns2)
        instant '[instant :refer :all]]
    (is (reduce
         #(or %1 (= %2 instant))
         false
         (tree-seq sequential? identity requires)))))

(deftest removes-unused-dependencies
  (let [new-ns (clean-ns ns-with-unused-deps)
        requires (get-ns-component new-ns :require)
        imports (get-ns-component new-ns :import)
        clean-requires (get-ns-component ns-without-unused-deps :require)
        clean-imports (get-ns-component ns-without-unused-deps :import)]
    (is (= clean-requires requires))
    (is (= clean-imports imports))))

(def artifact-ns '(ns refactor-nrepl.artifacts
                    (:require [clojure
                               [edn :as edn]
                               [string :as str]]
                              [clojure.data.json :as json]
                              [clojure.java.io :as io]
                              [clojure.tools.nrepl
                               [middleware :refer [set-descriptor!]]
                               [misc :refer [response-for]]
                               [transport :as transport]]
                              [org.httpkit.client :as http]
                              [refactor-nrepl.externs :refer [add-dependencies]])
                    (:import java.util.Date)))

(deftest test-pprint-artifact-ns
  (let [actual (pprint-ns artifact-ns)
        expected (slurp (.getAbsolutePath (File. "test/resources/artifacts_pprinted_no_indentation")))]
    (is (= expected actual))))

(deftest should-throw-on-cljs
  (is (thrown? IllegalArgumentException (clean-ns cljs-file))))

(comment
  ;; broken across versions due to difference in ordering of objects
  ;; in pprinted maps
  (deftest test-pprint
           (let [ns-str (pprint-ns (clean-ns ns1))
                 ns1-str (slurp (.getAbsolutePath (File. "test/resources/ns1_cleaned_no_indentation")))]
             (is (= ns1-str ns-str))))
  )
