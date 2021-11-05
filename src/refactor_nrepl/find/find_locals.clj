(ns refactor-nrepl.find.find-locals
  (:require
   [clojure.set :as set]
   [clojure.tools.analyzer.ast :refer [nodes]]
   [refactor-nrepl.analyzer :as ana]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.s-expressions :as sexp]))

(defn find-used-locals  [{:keys [file ^long line ^long column]}]
  {:pre [(number? line)
         (number? column)
         (not-empty file)]}
  (core/throw-unless-clj-file file)
  (let [content (slurp file)
        ast (ana/ns-ast content)
        sexp (sexp/get-enclosing-sexp content (dec line) (dec column))
        selected-sexp-node (->> ast
                                (ana/top-level-form-index line column)
                                (nth ast)
                                nodes
                                (filter (partial ana/node-at-loc? line column))
                                (filter (partial ana/node-for-sexp? sexp))
                                last)
        sexp-locals (->> selected-sexp-node
                         nodes
                         (filter #(= :local (:op %)))
                         (map :form)
                         distinct)
        avail-locals-in-use (set/intersection (->> selected-sexp-node
                                                   :env
                                                   :locals
                                                   keys
                                                   set)
                                              (set sexp-locals))]
    (filter avail-locals-in-use sexp-locals)))
