(ns refactor-nrepl.find-unbound
  (:require [clojure.set :as set]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [refactor-nrepl
             [analyzer :refer [ns-ast]]
             [util :refer :all]]))

(defn find-unbound-vars [{:keys [file line column]}]
  {:pre [(number? line)
         (number? column)
         (not-empty file)]}
  (throw-unless-clj-file file)
  (let [ast (-> file slurp ns-ast)
        selected-sexpr-node (->> ast
                                 (top-level-form-index line column)
                                 (nth ast)
                                 nodes
                                 (filter (partial node-at-loc? line column))
                                 reverse
                                 (remove #(#{:local :var :const} (:op %)))
                                 (sort-by #(- line (-> % :env :line)))
                                 first)]
    (into '() (set/intersection (->> selected-sexpr-node :env :locals keys set)
                                (->> selected-sexpr-node
                                     nodes
                                     (filter #(= :local (:op %)))
                                     (map :form)
                                     set)))))
