;;;; Copied from slamhound 1.5.5
;;;; Copyright © 2011-2012 Phil Hagelberg and contributors
;;;; Distributed under the Eclipse Public License, the same as Clojure.
(ns refactor-nrepl.ns.slam.hound.regrow
  (:require
   [nrepl.middleware.interruptible-eval :refer [*msg*]]
   [refactor-nrepl.ns.slam.hound.search :as search]))

(def ^:dynamic *cache* (atom {}))
(def ^:dynamic *dirty-ns* (atom #{}))

(defn wrap-clojure-repl [f]
  (fn [& args]
    (when-let [ns (some-> *msg* :ns symbol find-ns)]
      (swap! *dirty-ns* conj ns))
    (apply f args)))

(alter-var-root #'clojure.main/repl wrap-clojure-repl)

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

(defn candidates
  "Return a set of class or ns symbols that match the given constraints."
  [type missing _body _old-ns-map]
  (case type
    :import (into (ns-import-candidates missing)
                  (get @search/available-classes-by-last-segment missing))
    :refer (get (symbols->ns-syms) missing)))
