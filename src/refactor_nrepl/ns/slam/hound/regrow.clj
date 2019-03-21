;;;; Copied from slamhound 1.5.5
;;;; Copyright © 2011-2012 Phil Hagelberg and contributors
;;;; Distributed under the Eclipse Public License, the same as Clojure.
(ns refactor-nrepl.ns.slam.hound.regrow
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [refactor-nrepl.ns.slam.hound.future :refer [as->* cond->*]]
            [refactor-nrepl.ns.slam.hound.search :as search])
  (:import (clojure.lang IMapEntry IRecord)
           (java.util.regex Pattern)))

(def ^:dynamic *cache* (atom {}))
(def ^:dynamic *dirty-ns* (atom #{}))

(defn wrap-clojure-repl [f]
  (fn [& args]
    (swap! *dirty-ns* conj *ns*)
    (apply f args)))

(alter-var-root #'clojure.main/repl wrap-clojure-repl)

(defmacro ^:private caching [key & body]
  `(if *cache*
     (if-let [v# (get @*cache* ~key)]
       v#
       (let [v# (do ~@body)]
         (swap! *cache* assoc ~key v#)
         v#))
     (do ~@body)))

(defn cache-with-dirty-tracking
  "The function to be cached, f, should have two signatures. A zero-operand
  signature which computes the result for all namespaces, and a two-operand
  version which takes the previously computed result and a list of dirty
  namespaces, and returns an updated result."
  [key f]
  (if *cache*
    (if-let [cached (get @*cache* key)]
      (if-let [dirty (seq @*dirty-ns*)]
        (key (swap! *cache* assoc key (f cached dirty)))
        cached)
      (key (swap! *cache* assoc key (f))))
    (f)))

(defn clear-cache! []
  (when *cache*
    (reset! *cache* {})
    (reset! *dirty-ns* #{})))

(defn- all-ns-imports*
  ([]
   (all-ns-imports* {} (all-ns)))
  ([init namespaces]
   (reduce (fn [imports ns]
             (assoc imports ns (ns-imports ns)))
           init namespaces)))

(defn- all-ns-imports []
  (cache-with-dirty-tracking :all-ns-imports all-ns-imports*))

(defn- ns->symbols []
  (caching :ns->symbols
           (let [xs (all-ns)]
             (zipmap xs (mapv (comp set keys ns-publics) xs)))))

(defn- symbols->ns-syms*
  ([]
   (symbols->ns-syms* {} (all-ns)))
  ([init namespaces]
   (reduce
    (fn [m ns] (let [ns-sym (ns-name ns)]
                 (reduce
                  (fn [m k]
                    (assoc m k (conj (or (m k) #{}) ns-sym)))
                  m (keys (ns-publics ns)))))
    init namespaces)))

(defn- symbols->ns-syms []
  (cache-with-dirty-tracking :symbols->ns-syms symbols->ns-syms*))

(defn- walk
  "Adapted from clojure.walk/walk and clojure.walk/prewalk; this version
  preserves metadata on compound forms."
  [f form]
  (-> (cond
        (list? form) (apply list (map f form))
        (instance? IMapEntry form) (vec (map f form))
        (seq? form) (doall (map f form))
        (instance? IRecord form) (reduce (fn [r x] (conj r (f x))) form form)
        (coll? form) (into (empty form) (map f form))
        :else form)
      (as->* form'
        (if-let [m (meta form)]
          (with-meta form' m)
          form'))))

(defn- prewalk [f form]
  (walk (partial prewalk f) (f form)))

(defn- symbols-in-body [body]
  (filter symbol? (remove coll? (rest (tree-seq coll? seq body)))))

(defn- remove-var-form
  "Remove (var symbol) forms from body"
  [expr]
  (if (and (coll? expr) (= (first expr) 'var))
    nil
    expr))

(def ^:private ns-qualifed-syms
  (memoize
   (fn [body]
     (apply merge-with set/union {}
            (for [ss (symbols-in-body body)
                  :let [[_ alias var-name] (re-matches #"(.+)/(.+)" (str ss))]
                  :when alias]
              {(symbol alias) #{(symbol var-name)}})))))

(defn- ns-import-candidates
  "Search (all-ns) for imports that match missing-sym, returning a set of
  class symbols. This is slower than scanning through the list of static
  package names, but will successfully find dynamically created classes such
  as those created by deftype, defrecord, and definterface."
  [missing-sym]
  (reduce (fn [s imports]
            (if-let [^Class cls (get imports missing-sym)]
              (conj s (symbol (.getCanonicalName cls)))
              s))
          #{} (vals (all-ns-imports))))

(defn- alias-candidates [type missing body]
  (set
   (let [syms-with-alias (get (ns-qualifed-syms body) missing)]
     (when (seq syms-with-alias)
       (let [ns->syms (ns->symbols)]
         (for [ns (all-ns)
               :when (set/subset? syms-with-alias (ns->syms ns))]
           (ns-name ns)))))))

(defn candidates
  "Return a set of class or ns symbols that match the given constraints."
  [type missing body old-ns-map]
  (case type
    :import (into (ns-import-candidates missing)
                  (get @search/available-classes-by-last-segment missing))
    :alias (let [cs (alias-candidates type missing body)]
             (if (seq cs)
               cs
               ;; Try the alias search again without dynamically resolved vars
               ;; in case #' was used to resolve private vars in an aliased ns
               (let [body' (prewalk remove-var-form body)]
                 (if (= body' body)
                   cs
                   (alias-candidates type missing body')))))
    :refer (get (symbols->ns-syms) missing)
    :rename (reduce-kv
             (fn [s ns orig->rename]
               (cond->* s
                 (some #{missing} (vals orig->rename)) (conj ns)))
             #{} (:rename old-ns-map))))
