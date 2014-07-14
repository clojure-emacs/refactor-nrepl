(ns refactor-nrepl.refactor
  (:require [refactor-nrepl.analyzer :refer [string-ast]]
            [clojure.string :refer [split join]]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]))

(defn- find-referred [ast referred]
  (some #(= (symbol referred) (:class %)) (nodes (if (= 'ns (-> ast :fn :fn :class)) (dissoc ast :fn) ast))))

(defn- find-referred-reply [{:keys [transport ns-string referred] :as msg}]
  (let [ast (string-ast ns-string)
        result (find-referred ast referred)]
    (transport/send transport (response-for msg :value (when result (str result))))
    (transport/send transport (response-for msg :status :done))))

(defn- field-or-class [alias-info ast]
  (let [fn-node (:fn ast)
        class (:class fn-node)
        full-class (get alias-info class class)]
    (join "/" (remove nil? [full-class (:field fn-node)]))))

(defn- fns-invoked? [fns alias-info ast]
  (and (= :invoke (:op ast))
       (fns (field-or-class alias-info ast))))

(defn- find-invokes
  "Finds fn invokes in the AST.
   Returns a list of line, end-line, column, end-column and fn name tuples"
  [ast fn-names]
  (let [alias-info (:alias-info ast)
        fns (into #{} (split fn-names #","))]
    (->> ast
         nodes
         (filter (partial fns-invoked? fns alias-info))
         (map (juxt (comp :line :env)
                    (comp :end-line :env)
                    (comp :column :env)
                    (comp :end-column :env)
                    (partial field-or-class alias-info))))))

(defn- find-debug-fns-reply [{:keys [transport ns-string debug-fns] :as msg}]
  (let [ast (string-ast ns-string)
        result (find-invokes ast debug-fns)]
    (transport/send transport (response-for msg :value (when (not-empty result) result)))
    (transport/send transport (response-for msg :status :done))))

(defn wrap-refactor
  "Ensures that refactor only triggered with the right operation and forks to the appropriate refactor function"
  [handler]
  (fn [{:keys [op refactor-fn ns-string] :as msg}]
    (if (= "refactor" op)
      (cond (= "find-referred" refactor-fn) (find-referred-reply msg)
            (= "find-debug-fns" refactor-fn) (find-debug-fns-reply msg)
            :else
            (handler msg))
      (handler msg))))

(set-descriptor!
 #'wrap-refactor
 {:handles
  {"refactor"
   {:doc "Returns a an appropriate result for the given refactor fn.
          Currently available:
          - find-referred: searches for referred class in the AST returns the referred if found, nil otherwise
          - find-debug-fns: finds debug functions returns tuples containing
            [line-number end-line-number column-number end-column-number fn-name]"
    :requires {"ns-string" "the body of the namespace to build the AST with"
               "refactor-fn" "The refactor function to invoke"}
    :returns {"status" "done"
              "value" "result of the refactor"}}}})
