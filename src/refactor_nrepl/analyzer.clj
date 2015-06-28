(ns refactor-nrepl.analyzer
  (:refer-clojure :exclude [macroexpand-1])
  (:require [clojure.java.io :as io]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.jvm :as aj]
            [clojure.tools.analyzer.jvm.utils :as ajutils]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.tools.reader :as reader]
            [refactor-nrepl
             [util :as util]
             [config :as config]])
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
  (swap! ast-cache assoc ns {(hash file-content) ast})
  ast)

(defn- ns-on-cp? [ns]
  (io/resource (ajutils/ns->relpath ns)))

(defn- build-ast
  [ns aliases]
  (when (ns-on-cp? ns)
    (binding [ana/macroexpand-1 noop-macroexpand-1
              *file* (-> ns ajutils/ns-resource ajutils/source-path)
              reader/*data-readers* *data-readers*]
      (assoc-in (aj/analyze-ns ns) [0 :alias-info] aliases))))

(defn- cachable-ast [file-content]
  (let [[ns aliases] (parse-ns file-content)]
    (when ns
      (if-let [cached-ast-or-err (get-ast-from-cache ns file-content)]
        cached-ast-or-err
        (when-let [new-ast-or-err (try (build-ast ns aliases) (catch Throwable th th))]
          (update-ast-cache file-content ns new-ast-or-err))))))

(defn ns-ast
  "Build AST for a namespace"
  [file-content]
  (let [ast-or-err (cachable-ast file-content)]
    (cond
      (and (instance? Throwable ast-or-err)
           (config/get-opt :debug))
      (throw ast-or-err)

      (instance? Throwable ast-or-err)
      (throw (IllegalStateException.
              (str (first (parse-ns file-content)) " is in a bad state! Error: " (.getMessage ast-or-err))))

      :default
      ast-or-err)))

(defn warm-ast-cache []
  (doseq [f (util/find-clojure-sources-in-project)]
    (ns-ast (slurp f))))
