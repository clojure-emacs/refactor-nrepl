(ns refactor-nrepl.find.find-used-publics
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [refactor-nrepl.analyzer :as ana]
            [refactor-nrepl.find.find-macros :as macros]
            [refactor-nrepl.find.util :as find-util]
            [refactor-nrepl
             [core :as core]
             [tramp :as tramp]]))

(defn- ns-used-in-node?
  [used-ns ast-node]
  (= (the-ns (symbol used-ns)) (-> ast-node :meta :ns)))

(defn- normalize-protocol-name
  [protocol-name-str]
  (-> (str/replace protocol-name-str #"interface\s+" "")
      (str/replace "/" ".")
      (str/replace "#'" "")
      (str/replace "-" "_")))

(defn- node->loc-info [file symbol-name-pred node]
  (->> ((juxt (comp :line :env)
              (comp :end-line :env)
              (comp :column :env)
              (comp :end-column :env)
              (constantly file)
              symbol-name-pred)
        node)
       (zipmap [:line-beg :line-end :col-beg :col-end :file :name])))

(defn- protocol-used-in-node?
  [used-ns node]
  (let [ns-protocols (->> (symbol used-ns)
                          ns-publics
                          (filter (comp :method-builders deref val))
                          (map val)
                          (map str)
                          (map normalize-protocol-name)
                          set)]
    (some ns-protocols (->> (:interfaces node)
                            (map str)
                            (map normalize-protocol-name)))))

(defn- normalize-loc-info-name
  [norm-fn loc-info]
  (update-in loc-info [:name] norm-fn))

(defn- find-used-protocols
  [file used-ns ast-nodes]
  (let [protocol-pred (partial protocol-used-in-node? used-ns)]
    (->> (filter :interfaces ast-nodes)
         (filter protocol-pred)
         (map (partial node->loc-info file protocol-pred))
         (map (partial normalize-loc-info-name #(str/replace % #".*\." ""))))))

(defn- find-used-vars
  [file used-ns ast-nodes]
  (->> (filter (partial ns-used-in-node? used-ns) ast-nodes)
       (map (partial node->loc-info file :var))
       (remove (partial find-util/spurious? file))
       (map (partial normalize-loc-info-name core/normalize-var-name))))

(defn find-used-publics
  "Returns used symbols of the namespace given as `used-ns` in the file.

  Looks for used symbol types: vars, protocols and macros."
  [{:keys [file used-ns]}]
  (let [ast-nodes (->> file
                       tramp/remove-tramp-params
                       slurp
                       ana/ns-ast
                       rest
                       (mapcat nodes))
        macros (future (macros/find-used-macros file used-ns))
        vars (future (find-used-vars file used-ns ast-nodes))
        protocols (future (find-used-protocols file used-ns ast-nodes))]
    (set/union @vars @protocols @macros)))
