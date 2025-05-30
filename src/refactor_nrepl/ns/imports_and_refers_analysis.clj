;;;; Copied from slamhound 1.5.5
;;;; Copyright © 2011-2012 Phil Hagelberg and contributors
;;;; Distributed under the Eclipse Public License, the same as Clojure.
(ns refactor-nrepl.ns.imports-and-refers-analysis
  "Formerly known as `refactor-nrepl.ns.slam.hound.regrow`."
  (:require
   [refactor-nrepl.ns.class-search :as class-search]))

;; Benefits from `pmap` because ns-imports is somewhat expensive.
(defn- all-ns-imports
  ([]
   (all-ns-imports {} (all-ns)))
  ([init namespaces]
   (->> namespaces
        (pmap (fn [ns]
                [ns, (ns-imports ns)]))
        (into init))))

;; Doesn't need parallelization (unlike `all-ns-imports`),
;; as this defn is instantaneous.
(defn- symbols->ns-syms
  ([]
   (symbols->ns-syms {} (all-ns)))

  ([init namespaces]
   (reduce (fn [m ns]
             (let [ns-sym (ns-name ns)]
               (reduce (fn [m k]
                         (assoc m k (conj (or (m k) #{})
                                          (with-meta ns-sym
                                            {:refactor-nrepl/symbol k}))))
                       m
                       (keys (ns-publics ns)))))
           init
           namespaces)))

(defn- ns-import-candidates
  "Search (all-ns) for imports that match missing-sym, returning a set of
  class symbols. This is slower than scanning through the list of static
  package names, but will successfully find dynamically created classes such
  as those created by deftype, defrecord, and definterface."
  [missing-sym]
  (reduce (fn [s imports]
            (if-let [^Class cls (get imports missing-sym)]
              (->> cls .getCanonicalName symbol (conj s))
              s))
          #{} (vals (all-ns-imports))))

(defn candidates
  "Return a set of class or ns symbols that match the given constraints."
  [type missing _body _old-ns-map]
  (case type
    :import (into #{}
                  (map (fn [s]
                         (with-meta s
                           {:refactor-nrepl/is-class true})))
                  (reduce into #{} [(ns-import-candidates missing)
                                    (get (class-search/available-classes-by-last-segment) missing)]))
    :refer  (get (symbols->ns-syms) missing)))
