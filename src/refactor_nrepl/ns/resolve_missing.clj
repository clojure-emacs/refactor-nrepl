(ns refactor-nrepl.ns.resolve-missing
  "Resolve a missing symbol on the classpath."
  (:require [cider.nrepl.middleware.info :refer [info-clj]]
            [cider.nrepl.middleware.util.cljs :as cljs]
            [cljs-tooling.util.analysis :as cljs-ana]
            [refactor-nrepl.ns.helpers :refer [prefix suffix]]
            [refactor-nrepl.ns.slam.hound.regrow :as slamhound]))

(defn- candidates [sym]
  (reduce into
          [(when-let [p (prefix sym)]
             (slamhound/candidates :import (symbol p) [] {}))
           (slamhound/candidates :import (symbol (suffix sym)) [] {})
           (slamhound/candidates :refer sym [] {})]))

(defn- get-type [sym]
  (let [info (info-clj 'user sym)]
    (if-let [clazz (:class info)]
      (cond
        ((set (:interfaces info)) 'clojure.lang.IType) :type
        ((set (:interfaces info)) 'clojure.lang.IRecord) :type
        :else :class)                   ; interfaces are included here
      :ns)))

(defn- collate-type-info
  [candidates]
  (map #(list % (get-type %)) candidates))

(defn- inlined-dependency? [candidate]
  (or (-> candidate str (.startsWith "deps."))
      (-> candidate str (.startsWith "mranderson"))
      (-> candidate str (.startsWith "eastwood.copieddeps"))))

(defn- ns-publics-cljs [env ns-name]
  (->> ns-name (cljs-ana/public-vars env) keys))

(defn- ns-public-macros-cljs [ns-name]
  (->> ns-name cljs-ana/public-macros keys))

(defn- cljs-vars-to-namespaces [env]
  (let [all-ns (remove #(= % 'cljs.core) (keys (cljs-ana/all-ns env)))
        all-ns-and-vars (map (fn [ns]
                               (zipmap (remove nil? (ns-publics-cljs env ns))
                                       (repeat (list (list ns :ns)))))
                             all-ns)
        all-ns-and-macros (map (fn [ns]
                                 (zipmap (remove nil? (ns-public-macros-cljs ns))
                                         (repeat (list (list ns :macro)))))
                               all-ns)]
    (into
     (apply merge-with conj all-ns-and-vars)
     (apply merge-with conj all-ns-and-macros))))

(defn resolve-missing [{sym :symbol :as msg}]
  (when-not (and sym (string? sym) (seq sym))
    (throw (IllegalArgumentException.
            (str "Invalid input to resolve missing: '" sym "'"))))
  (if-let [env (cljs/grab-cljs-env msg)]
    (some->> sym
             symbol
             (get (cljs-vars-to-namespaces env))
             pr-str)
    (some->> sym
             symbol
             candidates
             (remove inlined-dependency?)
             collate-type-info
             pr-str)))
