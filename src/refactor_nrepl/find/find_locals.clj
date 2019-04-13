(ns refactor-nrepl.find.find-locals
  (:require [clojure.set :as set]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [refactor-nrepl.analyzer :as ana]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.s-expressions :as sexp]))

(defn find-used-locals  [{:keys [file ^long line ^long column]}]
  {:pre [(number? line)
         (number? column)
         (not-empty file)]}
  ;(core/throw-unless-clj-file file)
  (let [content (slurp file)
        cljs? (core/cljs-file? file)
        ;; fork for cljs using `cljs.analyzer` directly
        ast (if cljs? (ana/build-cljs-ast content) (ana/ns-ast content))
        sexp (sexp/get-enclosing-sexp content (dec line) (dec column))
        ;; work around for cljs ASTs not having end-line and end-column info
        top-level-form-index-fn (if cljs? ana/top-level-form-index-cljs ana/top-level-form-index)
        ;; work around the fact that cljs ASTs don't have raw-forms in them. the original form before macro expansion can not be reproduced
        node-for-sexp-fn (if cljs? ana/node-for-sexp-cljs? ana/node-for-sexp?)
        selected-sexp-node (->> ast
                                (top-level-form-index-fn line column)
                                (nth ast)
                                nodes
                                ((fn [nds]
                                   (if cljs?
                                     nds; can't use `node-at-loc-cljs?` after `nodes`
                                      (filter (partial ana/node-at-loc? line column) nds))))
                                (filter (partial node-for-sexp-fn sexp))
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
