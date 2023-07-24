(ns refactor-nrepl.ns.resolve-missing
  "Resolve a missing symbol on the classpath."
  (:require
   [cider.nrepl.middleware.util.cljs :as cljs]
   [clojure.string :as string]
   [orchard.cljs.analysis :as cljs-ana]
   [orchard.info]
   [refactor-nrepl.core :refer [prefix suffix]]
   [refactor-nrepl.ns.imports-and-refers-analysis :as imports-and-refers-analysis]
   [refactor-nrepl.util :refer [self-referential?]]))

(defn- candidates [sym]
  (reduce into
          []
          [(when-let [p (prefix sym)]
             (imports-and-refers-analysis/candidates :import (symbol p) [] {}))
           (imports-and-refers-analysis/candidates :import (symbol (suffix sym)) [] {})
           (imports-and-refers-analysis/candidates :refer (symbol (suffix sym)) [] {})]))

(defn- get-type [maybe-ns-sym sym]
  (let [info (orchard.info/info (or maybe-ns-sym 'user)
                                sym)
        found? (:class info)
        interfaces (if found?
                     (some-> info :interfaces set)
                     #{})]
    (cond
      (interfaces 'clojure.lang.IType)       :type
      (interfaces 'clojure.lang.IRecord)     :type
      found?                                 :class ;; interfaces are included here
      (-> sym meta :refactor-nrepl/is-class) :class
      :else                                  :ns)))

(defn- collate-type-info [maybe-ns-sym candidates]
  (mapv (fn [candidate]
          (let [found-ns (some-> maybe-ns-sym find-ns)]

            (try
              (cond-> {:name candidate
                       :type (get-type maybe-ns-sym candidate)}
                found-ns
                (assoc :already-interned (if (-> candidate meta :refactor-nrepl/is-class)
                                           (boolean (when-let [imports (some-> found-ns ns-imports)]
                                                      (get imports (-> candidate str (string/split #"\.") last symbol))))
                                           (boolean (when-let [refers (some-> found-ns ns-refers)]
                                                      (let [var-namespace= (fn [var-ref]
                                                                             (-> var-ref
                                                                                 meta
                                                                                 :ns
                                                                                 ns-name
                                                                                 (= candidate)))
                                                            candidate-symbol (-> candidate meta :refactor-nrepl/symbol)]
                                                        (or (some-> refers ;; refer
                                                                    (get candidate-symbol)
                                                                    (var-namespace=))
                                                            (some->> refers ;; refer + rename
                                                                     (some (fn [[k v]]
                                                                             (when (and (var-namespace= v)
                                                                                        (-> v
                                                                                            meta
                                                                                            :name
                                                                                            (= candidate-symbol)))
                                                                               k)))))))))))

              ;; This happends when class `candidate` depends on a class that is
              ;; not available on the classpath.
              (catch NoClassDefFoundError e
                (refactor-nrepl.util/maybe-log-exception e)
                {:name candidate :type :class}))))
        candidates))

(defn- ns-publics-cljs [env ns-name]
  (->> ns-name (cljs-ana/public-vars env) keys))

(defn- ns-public-macros-cljs [env ns-name]
  (->> ns-name (cljs-ana/public-macros env) keys))

(defn- cljs-vars-to-namespaces [env]
  (let [all-ns (remove (partial = 'cljs.core) (keys (cljs-ana/all-ns env)))
        ns-by-vars (->> all-ns
                        (mapcat (fn [ns]
                                  (map (fn [sym] {sym (list {:name ns :type :ns})})
                                       (ns-publics-cljs env ns))))
                        (remove empty?)
                        (apply merge-with into))
        ns-by-macros (->> all-ns
                          (mapcat (fn [ns]
                                    (map (fn [sym] {sym (list {:name ns :type :macro})})
                                         (ns-public-macros-cljs env ns))))
                          (remove empty?)
                          (apply merge-with into))]
    (merge-with into ns-by-vars ns-by-macros)))

(defn resolve-missing [{sym :symbol
                        ns-str :ns
                        jvm? :refactor-nrepl.internal/force-jvm?
                        :as msg}]
  (when (or (not (string? sym)) (string/blank? sym))
    (throw (IllegalArgumentException.
            (str "Invalid input to resolve-missing: '" sym "'"))))
  (let [ns-sym (some-> ns-str not-empty symbol)]
    (if-let [env (and (not jvm?)
                      (cljs/grab-cljs-env msg))]
      (some->> sym
               suffix
               symbol
               (get (cljs-vars-to-namespaces env))
               pr-str)
      (some->> sym
               symbol
               candidates
               (remove self-referential?)
               (collate-type-info ns-sym)
               pr-str))))
