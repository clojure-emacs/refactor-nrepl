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
        selected-sexpr (->> ast
                            (top-level-form-index line column)
                            (nth ast)
                            nodes
                            (filter (partial node-at-loc? line column))
                            reverse
                            (drop-while #(#{:local :var :const} (:op %)))
                            first)]
    (into '() (set/intersection (->> selected-sexpr :env :locals keys set)
                                (->> selected-sexpr
                                     nodes
                                     (filter #(= :local (:op %)))
                                     (map :form)
                                     set)))))
