(ns refactor-nrepl.test-namespace
  (:require [clojure.test :refer :all]
            [refactor-nrepl.ns
             [clean-ns :refer [clean-ns read-ns-form]]
             [helpers :refer [get-ns-component]]]
            [refactor-nrepl.ns.pprint :refer [pprint-ns]])
  (:import java.io.File))

(def ns1 (.getAbsolutePath (File. "test/resources/ns1.clj")))
(def ns1-sorted (read-ns-form (.getAbsolutePath (File. "test/resources/ns1_sorted.clj"))))
(def ns2 (.getAbsolutePath (File. "test/resources/ns2.clj")))
(def ns2-collapsed (read-ns-form
                    (.getAbsolutePath (File. "test/resources/ns2_collapsed.clj"))))
(def ns-with-exclude (read-ns-form
                      (.getAbsolutePath (File. "test/resources/ns_with_exclude.clj"))))
(def ns-with-unused-deps (.getAbsolutePath (File. "test/resources/unused_deps.clj")))
(def ns-without-unused-deps (read-ns-form
                             (.getAbsolutePath (File. "test/resources/unused_removed.clj"))))
((deftest collapses-requires
   (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                 (fn [_ libspec] libspec)]
     (let [requires (get-ns-component (clean-ns ns2) :require)
           collapsed-requires (get-ns-component ns2-collapsed :require)]
       (is (= collapsed-requires requires))))))

(deftest preserves-removed-use
  (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                (fn [_ libspec] libspec)]
    (let [requires (get-ns-component (clean-ns ns2) :use)
          collapsed-requires (get-ns-component ns2-collapsed :require)]
      (is (reduce
           #(or %1 (= %2 '[clojure [edn :refer :all :rename {read-string rs
                                                             read rd}]
                           [string :refer :all :rename {replace foo reverse bar}]
                           [test :refer :all]]))
           false
           (tree-seq sequential? identity collapsed-requires))))))

(deftest removes-use-with-rename-clause
  (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                (fn [_ libspec] libspec)]
    (let [requires (get-ns-component (clean-ns ns2) :use)
          collapsed-requires (get-ns-component ns2-collapsed :require)]
      (is (reduce
           #(or %1 (= %2 '[edn :refer :all :rename {read-string rs
                                                    read rd}]))
           false
           (tree-seq sequential? identity collapsed-requires))))))

(deftest test-sort-and-prefix-favoring
  (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                (fn [_ libspec] libspec)
                refactor-nrepl.ns.dependencies/remove-unused-imports
                (fn [_ imports] imports)]
    (let [requires (get-ns-component (clean-ns ns1) :require)
          imports (get-ns-component (clean-ns ns1) :import)
          sorted-requires (get-ns-component ns1-sorted :require)
          sorted-imports (get-ns-component ns1-sorted :import)]
      (is (= sorted-requires requires))
      (is (= sorted-imports imports)))))

(deftest throws-exceptions-for-unexpected-elements
  (is (thrown? IllegalArgumentException
               (clean-ns ns-with-exclude))))

(deftest throws-on-malformed-ns
  (is (thrown? IllegalArgumentException
               (read-ns-form (.getAbsolutePath
                              (File. "test/resources/clojars-artifacts.edn"))))))

(deftest preserves-other-elements
  (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                (fn [_ libspec] libspec)
                refactor-nrepl.ns.dependencies/remove-unused-imports
                (fn [_ imports] imports)]
    (let [actual (clean-ns ns1)]
      (is (= ns1-sorted actual)))))

(deftest removes-use
  (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                (fn [_ libspec] libspec)]
    (let [use-clause (get-ns-component ns1-sorted :use)]
      (is (= use-clause nil)))))

(deftest combines-multiple-refers
  (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                (fn [_ libspec] libspec)]
    (let [requires (clean-ns ns2)
          refers '[deref-env with-env]]
      (is (reduce
           #(or %1 (= %2 refers))
           false
           (tree-seq sequential? identity requires))))))

(deftest combines-multiple-refers-to-all
  (with-redefs [refactor-nrepl.ns.dependencies/remove-unused-requires
                (fn [_ libspec] libspec)]
    (let [requires (clean-ns ns2)
          ast '[ast :refer :all]]
      (is (reduce
           #(or %1 (= %2 ast))
           false
           (tree-seq sequential? identity requires))))))

(deftest removes-unused-dependencies
  (let [new-ns (clean-ns ns-with-unused-deps)
        requires (get-ns-component new-ns :require)
        imports (get-ns-component new-ns :import)
        clean-requires (get-ns-component ns-without-unused-deps :require)
        clean-imports (get-ns-component ns-without-unused-deps :import)]
    (is (= clean-requires requires))
    (is (= clean-imports imports))))
