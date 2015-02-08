(ns refactor-nrepl.analyzer
  (:refer-clojure :exclude [macroexpand-1])
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer
             [jvm :as aj]
             [passes :refer [schedule]]]
            [clojure.tools.analyzer.passes.emit-form :as emit-form]
            [clojure.tools.analyzer.passes.jvm.validate :refer [validate]]
            [clojure.tools.namespace.parse :refer [read-ns-decl]])
  (:import java.io.PushbackReader))

;;; The structure here is {ns {content-hash ast}}
(def ^:private ast-cache (atom {}))

(defn get-alias [as v]
  (cond as (first v)
        (= (first v) :as) (get-alias true (rest v))
        :else (get-alias nil (rest v))))

(defn parse-ns
  "Returns tuples with the ns as the first element and
  a map of the aliases for the namespace as the second element
  in the same format as ns-aliases"
  [body]
  (let [ns-decl (read-ns-decl (PushbackReader. (java.io.StringReader. body)))
        aliases (->> ns-decl
                     (filter list?)
                     (some #(when (#{:require} (first %)) %))
                     rest
                     (remove symbol?)
                     (filter #(contains? (set %) :as))
                     (#(zipmap (map (partial get-alias nil) %)
                               (map first %))))]
    [(second ns-decl) aliases]))

(defn- noop-macroexpand-1 [form]
  form)

(defn- get-ast-from-cache
  [ns file-content]
  (-> @ast-cache
      (get ns)
      (get (hash file-content))))

(defn- update-ast-cache
  [file-content ns ast]
  (swap! ast-cache update-in [ns] merge {(hash file-content) ast})
  ast)

(defn- build-ast
  [ns aliases]
  (binding [ana/macroexpand-1 noop-macroexpand-1]
    (assoc-in (aj/analyze-ns ns) [0 :alias-info] aliases)))

(defn- cachable-ast [file-content]
  (let [[ns aliases] (parse-ns file-content)]
    (when ns
      (if-let [cached-ast (get-ast-from-cache ns file-content)]
        cached-ast
        (update-ast-cache file-content ns (build-ast ns aliases))))))

(defn ns-ast
  "builds AST for a namespace"
  [file-content]
  (try
    (cachable-ast file-content)
    (catch Exception ex
      (println "error when building AST for" (first (parse-ns file-content)))
      (.printStackTrace ex)
      [])))

;;; Used in eval+analyze to emit code for later evaluation
;;; This isn't really of interest to us, so this is a no-op
(defmethod clojure.tools.analyzer.passes.emit-form/-emit-form ::unresolved-sym
  [& _])

(defn find-unbound-vars [namespace]
  (let [unbound (atom #{})]
    (binding [aj/run-passes (schedule #{#'validate})
              ana/macroexpand-1 noop-macroexpand-1]
      (aj/analyze-ns namespace (aj/empty-env)
                     {:passes-opts
                      {:validate/unresolvable-symbol-handler
                       (fn [_ var-name orig-ast]  (swap! unbound conj var-name)
                         {:op ::unresolved-sym :sym var-name :env (:env orig-ast)
                          :form (:form orig-ast)})}}))
    @unbound))
