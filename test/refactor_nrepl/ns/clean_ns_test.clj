(ns refactor-nrepl.ns.clean-ns-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [are deftest is]]
   [refactor-nrepl.config :as config]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.ns.clean-ns :refer [clean-ns]]
   [refactor-nrepl.ns.pprint :refer [pprint-ns]])
  (:import
   (java.io File)))

(defn- absolute-path [^String path]
  (.getAbsolutePath (File. path)))

(defn- clean-msg [path]
  {:path (absolute-path path)
   :relative-path path})

(def ns1 (clean-msg "test-resources/ns1.clj"))
(def ns1-cleaned (core/read-ns-form-with-meta (absolute-path "test-resources/ns1_cleaned.clj")))
(def ns2 (clean-msg "test-resources/ns2.clj"))
(def ns2-cleaned (core/read-ns-form-with-meta (absolute-path "test-resources/ns2_cleaned.clj")))
(def ns2-meta (clean-msg "test-resources/ns2_meta.clj"))
(def ns3 (clean-msg "test-resources/ns3.clj"))
(def ns3-rebuilt (core/read-ns-form-with-meta (absolute-path "test-resources/ns3_rebuilt.clj")))
(def ns-with-exclude (clean-msg "test-resources/ns_with_exclude.clj"))
(def ns-with-rename (clean-msg "test-resources/ns_with_rename.clj"))
(def ns-with-rename-cleaned (core/read-ns-form-with-meta "test-resources/ns_with_rename_cleaned.clj"))
(def ns-with-unused-deps (clean-msg "test-resources/unused_deps.clj"))
(def ns-without-unused-deps (core/read-ns-form-with-meta
                             (absolute-path "test-resources/unused_removed.clj")))
(def cljs-file (clean-msg "test-resources/file.cljs"))
(def ns-referencing-macro (absolute-path "test-resources/ns_referencing_macro.clj"))
(def cljs-ns (clean-msg "test-resources/cljsns.cljs"))
(def cljs-ns-cleaned (core/read-ns-form-with-meta (absolute-path "test-resources/cljsns_cleaned.cljs")))

(def cljc-ns (clean-msg "test-resources/cljcns.cljc"))
(def cljc-ns-cleaned-clj (core/read-ns-form-with-meta (absolute-path "test-resources/cljcns_cleaned.cljc")))
(def cljc-ns-cleaned-cljs (core/read-ns-form-with-meta :cljs (absolute-path "test-resources/cljcns_cleaned.cljc")))

(def cljc-ns-same-clj-cljs (clean-msg "test-resources/cljcns_same_clj_cljs.cljc"))
(def cljc-ns-same-clj-cljs-cleaned (core/read-ns-form-with-meta (absolute-path "test-resources/cljcns_same_clj_cljs_cleaned.cljc")))

(def ns-with-shorthand-meta (clean-msg "test-resources/ns_with_shorthand_meta.clj"))
(def ns-with-multiple-shorthand-meta (clean-msg "test-resources/ns_with_multiple_shorthand_meta.clj"))
(def ns-with-gen-class-methods-meta (clean-msg "test-resources/ns_with_gen_class_methods_meta.clj"))
(def ns-with-gen-class-methods-meta-clean (clean-msg "test-resources/ns_with_gen_class_methods_meta_clean.clj"))
(def ns-with-lots-of-meta (clean-msg "test-resources/ns_with_lots_of_meta.clj"))
(def ns-with-lots-of-meta-clean (clean-msg "test-resources/ns_with_lots_of_meta_clean.clj"))

(def ns-with-inner-classes (clean-msg "test-resources/ns_with_inner_classes.clj"))

(def ns-using-dollar (clean-msg "test-resources/ns_using_dollar.clj"))

(def ns1-relative-path {:path "I do not exist.clj"
                        :relative-path "test-resources/ns1.clj"})

(def ns-with-npm-strs (clean-msg "test-resources/ns_with_npm_strs.cljs"))
(def ns-with-npm-strs-clean (clean-msg "test-resources/ns_with_npm_strs_clean.cljs"))

(def ns-with-whitespace-changes-only (clean-msg "test-resources/ns_with_whitespace_changes_only.clj"))

(deftest combines-requires
  (let [prefix-requires (config/with-config {:prefix-rewriting true}
                          (core/get-ns-component (clean-ns ns2) :require))
        combined-requires (core/get-ns-component ns2-cleaned :require)]
    (is (= combined-requires prefix-requires))))

(deftest meta-preserved
  (let [cleaned (pprint-ns (clean-ns ns2-meta))]
    (is (str/includes? cleaned "^{:author \"Trurl and Klapaucius\"
      :doc \"test ns with meta\"}"))))

(deftest rewrites-use-to-require
  (let [combined-requires (core/get-ns-component ns2-cleaned :require)]
    (is (reduce
         #(or %1 (= %2 '[clojure
                         [edn :refer :all :rename {read-string rs}]
                         [instant :refer :all]
                         [pprint :refer [cl-format fresh-line get-pretty-writer]]
                         [string :refer :all]
                         [test :refer :all]]))
         false
         (tree-seq sequential? identity combined-requires)))))

(deftest keeps-clause-with-rename
  (let [combined-requires (core/get-ns-component ns2-cleaned :require)]
    (is (reduce
         #(or %1 (= %2 '[edn :refer :all :rename {read-string rs}]))
         false
         (tree-seq sequential? identity combined-requires)))))

(deftest test-sort-and-prefix-favoring
  (let [requires (core/get-ns-component
                  (config/with-config {:prefix-rewriting true}
                    (clean-ns ns1)) :require)
        imports (core/get-ns-component (clean-ns ns1) :import)
        sorted-requires (core/get-ns-component ns1-cleaned :require)
        sorted-imports (core/get-ns-component ns1-cleaned :import)
        collize (fn [coll transform-to]
                  (->> coll (map (fn [x]
                                   (cond-> x
                                     (and (not (coll? x))
                                          (not (keyword? x)))
                                     transform-to)))))]
    (is (= (collize sorted-requires vector) requires))
    (is (= (collize sorted-imports list) imports))))

(deftest throws-exceptions-for-unexpected-elements
  (is (thrown? IllegalArgumentException
               (clean-ns ns-with-exclude))))

(deftest throws-on-malformed-ns
  (is (thrown? IllegalStateException
               (core/read-ns-form-with-meta (.getAbsolutePath
                                             (File. "test-resources/clojars-artifacts.edn"))))))

(deftest preserves-other-elements
  (let [actual (clean-ns ns1)
        docstring (nth actual 2)
        author (nth actual 3)
        refer-clojure (nth actual 4)
        gen-class (nth actual 5)]
    (is (= (nth ns1-cleaned 2) docstring))
    (is (= (nth ns1-cleaned 3) author))
    (is (= (nth ns1-cleaned 4) refer-clojure))
    (is (= (nth ns1-cleaned 5) gen-class))))

(deftest removes-use
  (let [use-clause (core/get-ns-component ns1-cleaned :use)]
    (is (nil? use-clause))))

(deftest combines-multiple-refers
  (let [requires (clean-ns ns2)
        refers '[cl-format fresh-line get-pretty-writer]]
    (is (reduce
         #(or %1 (= %2 refers))
         false
         (tree-seq sequential? identity requires)))))

(deftest combines-multiple-refer-alls
  (let [[_ & libspecs] (core/get-ns-component (clean-ns ns2) :require)
        instant '[clojure.instant :refer :all]]
    (is (= (:count (reduce
                    (fn [acc libspec]
                      (if (= libspec instant)
                        (update acc :count inc)
                        acc))
                    {:count 0}
                    libspecs))
           1)
        "Exactly one libspec present for duplicated :refer :all clause")))

(deftest removes-unused-dependencies
  (let [new-ns (clean-ns ns-with-unused-deps)
        requires (core/get-ns-component new-ns :require)
        imports (core/get-ns-component new-ns :import)
        clean-requires (core/get-ns-component ns-without-unused-deps :require)
        clean-imports (core/get-ns-component ns-without-unused-deps :import)]
    (is (= clean-requires requires))
    (is (= clean-imports imports))))

(def artifact-ns
  '(ns refactor-nrepl.artifacts
     (:require
      [clojure
       [edn :as edn]
       [string :as str]]
      [clojure.data.json :as json]
      [clojure.java.io :as io]
      [nrepl
       [middleware :refer [set-descriptor!]]
       [misc :refer [response-for]]
       [transport :as transport]]
      [org.httpkit.client
       :as very-very-very-very-long-alias-causing-line-wrap]
      [refactor-nrepl.externs :refer [add-dependencies]])
     (:import java.util.Date)))

(deftest test-pprint-artifact-ns
  (are [setting filename] (let [actual (config/with-config {:insert-newline-after-require setting}
                                         (pprint-ns (with-meta artifact-ns nil)))
                                expected (-> filename File. .getAbsolutePath slurp)]
                            (is (= expected actual))
                            true)
    true  "test-resources/artifacts_pprinted"
    false "test-resources/artifacts_pprinted_traditional_newline"))

(deftest handles-imports-when-only-enum-is-used
  (let [new-ns (clean-ns ns2)
        imports (core/get-ns-component new-ns :import)]
    (is (some #(= '(java.text Normalizer) %) imports))))

(deftest keeps-referred-macros-around
  (let [new-ns (clean-ns (clean-msg ns-referencing-macro))]
    ;; nil means no changes
    (is (nil? new-ns))))

(deftest handles-clojurescript-files
  (let [actual (clean-ns cljs-ns)]
    (is (= cljs-ns-cleaned actual))))

(deftest handles-cljc-files
  (let [new-ns (str (clean-ns cljc-ns))
        new-clj-ns (core/ns-form-from-string new-ns)
        new-cljs-ns (core/ns-form-from-string :cljs new-ns)]
    (is (= cljc-ns-cleaned-clj new-clj-ns))
    (is (= cljc-ns-cleaned-cljs new-cljs-ns))))

(deftest does-not-use-read-conditionals-when-ns-are-equal
  (is (= (clean-ns cljc-ns-same-clj-cljs)
         cljc-ns-same-clj-cljs-cleaned)))

(deftest respects-no-prune-option
  (config/with-config {:prune-ns-form false
                       :prefix-rewriting true}
    (let [new-require (core/get-ns-component (clean-ns ns3) :require)
          expected-require (core/get-ns-component ns3-rebuilt :require)]
      (is (= expected-require new-require)))))

(deftest does-not-remove-ns-with-rename
  (is (= (nthrest ns-with-rename-cleaned 2) (nthrest (clean-ns ns-with-rename) 2))))

(deftest test-pprint
  (let [ns-str (pprint-ns (clean-ns ns1))
        ns1-str (slurp "test-resources/ns1_cleaned_and_pprinted")
        ns1-prefix-notation (slurp "test-resources/ns1_cleaned_and_pprinted_prefix_notation")]
    (is (= ns1-str ns-str))
    (is (= ns1-prefix-notation (config/with-config
                                 {:prefix-rewriting true}
                                 (pprint-ns (clean-ns ns1)))))))

(deftest preserves-shorthand-meta
  (let [cleaned (pprint-ns (clean-ns ns-with-shorthand-meta))]
    (is (re-find #"\^:automation" cleaned))))

(deftest preserves-multiple-shortand-meta
  (let [cleaned (pprint-ns (clean-ns ns-with-multiple-shorthand-meta))]
    (is (re-find #"\^:automation" cleaned))
    (is (re-find #"\^:multiple" cleaned))))

(deftest preserves-gen-class-methods-meta
  (let [actual (pprint-ns (clean-ns ns-with-gen-class-methods-meta))
        expected (slurp (:path ns-with-gen-class-methods-meta-clean))]
    (is (= expected actual))))

(deftest preserves-all-meta
  (config/with-config {:prefix-rewriting false}
    (let [actual (pprint-ns (clean-ns ns-with-lots-of-meta))
          expected (slurp (:path ns-with-lots-of-meta-clean))]
      (is (= expected actual)))))

(deftest does-not-remove-dollar-sign-if-valid-symbol
  (let [cleaned (pprint-ns (clean-ns ns-using-dollar))]
    (is (re-find #"\[\$\]" cleaned))))

(deftest does-not-break-import-for-inner-class
  (is (nil? (clean-ns ns-with-inner-classes))))

(deftest fallback-to-relative-path
  (is (= (pprint-ns (clean-ns ns1))
         (pprint-ns (clean-ns ns1-relative-path)))))

;; keep quotes around string requires
(deftest npm-string-preservation
  (let [cleaned (pprint-ns (clean-ns ns-with-npm-strs))]
    (is (re-find #"\[\"react-native\" :as rn\]" cleaned))))

;; group string requires together when sorting
(deftest npm-string-sorting
  (is (= (pprint-ns (clean-ns ns-with-npm-strs))
         (pprint-ns (read-string (slurp (:path ns-with-npm-strs-clean)))))))

(deftest whitespace-only-changes-are-ignored-by-default
  (is (nil? (clean-ns ns-with-whitespace-changes-only))))

(deftest whitespace-only-changes-are-considered-when-always-return-ns-form-option-is-true
  (is (= (read-string (slurp (:path ns-with-whitespace-changes-only)))
         (clean-ns (assoc ns-with-whitespace-changes-only
                          :always-return-ns-form true)))))

(core/with-clojure-version->= {:major 1 :minor 11}
  (deftest as-alias
    (is (= '(ns as-alias (:require [foo :as-alias f]))
           (clean-ns (clean-msg "test-resources/clean_ns/as_alias.cljc"))))))
