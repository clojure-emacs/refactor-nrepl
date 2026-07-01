(ns refactor-nrepl.analyzer-test
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.analyzer :as sut]))

(deftest ns-ast-test
  (doseq [f ["core_async_usage.clj"
             "clashing_defprotocol_method_name.clj"
             ;; https://github.com/clojure-emacs/refactor-nrepl/issues/347
             "uses_warn_on_reflection.clj"
             "uses_warn_on_reflection_cljc.cljc"]
          :let [c (-> f io/resource slurp)]]
    (is (some? (sut/ns-ast c)))))

(deftest warm-ast-cache-test
  (let [pairs (partition 2 (sut/warm-ast-cache))]
    (when (System/getenv "CI")
      (pprint/pprint pairs))
    (doseq [[ns-sym result] pairs]
      (is (simple-symbol? ns-sym))
      (is (or (= "OK" result)
              (and (list? result)
                   (-> result first #{"error"})
                   (-> result second string?)))))))

(deftest ast-cache-respects-limit
  (let [cache (deref #'sut/ast-cache)
        access (deref #'sut/ast-cache-access)
        orig-limit @sut/ast-cache-limit
        saved-cache @cache
        saved-access @access]
    (try
      (reset! sut/ast-cache-limit 2)
      (reset! cache {})
      (reset! access {:ticks {} :counter 0})
      ;; three distinct, freshly-analyzed namespaces -> three cache inserts
      (doseq [f ["core_async_usage.clj"
                 "clashing_defprotocol_method_name.clj"
                 "uses_warn_on_reflection.clj"]]
        (sut/ns-ast (-> f io/resource slurp)))
      (is (<= (count @cache) 2)
          "LRU eviction keeps the AST cache within the configured limit")
      (finally
        (reset! sut/ast-cache-limit orig-limit)
        (reset! cache saved-cache)
        (reset! access saved-access)))))
