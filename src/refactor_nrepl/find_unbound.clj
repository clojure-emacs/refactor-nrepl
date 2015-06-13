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
  (let [content (slurp file)
        ast (ns-ast content)
        sexp (get-enclosing-sexp content (dec line) (dec column))
        selected-sexp-node (->> ast
                                (top-level-form-index line column)
                                (nth ast)
                                nodes
                                (filter (partial node-at-loc? line column))
                                (filter (partial node-for-sexp? sexp))
                                last)
        avail-locals (->> selected-sexp-node :env :locals keys)
        locals-in-use (set/intersection (set avail-locals)
                              (->> selected-sexp-node
                                   nodes
                                   (filter #(= :local (:op %)))
                                   (map :form)
                                   set))]
    (filter locals-in-use avail-locals)))
