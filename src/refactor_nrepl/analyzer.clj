(ns refactor-nrepl.analyzer
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.analyzer.env :refer [with-env]]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.passes :refer [schedule]]
            [clojure.tools.analyzer.passes.jvm
             [box :refer [box]]
             [constant-lifter :refer [constant-lift]]
             [classify-invoke :refer [classify-invoke]]
             [infer-tag :refer [infer-tag]]
             [validate-loop-locals :refer [validate-loop-locals]]
             [analyze-host-expr :refer [analyze-host-expr]]]
            [clojure.tools.analyzer.passes
             [source-info :refer [source-info]]
             [elide-meta :refer [elide-meta]]
             [warn-earmuff :refer [warn-earmuff]]
             [uniquify :refer [uniquify-locals]]]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rts])
  (:import java.io.PushbackReader))

(def e (ana.jvm/empty-env))

;; these two fns could go to clojure.tools.namespace.parse: would worth a pull request
(defn get-alias [as v]
  (cond as (first v)
        (= (first v) :as) (get-alias true (rest v))
        :else (get-alias nil (rest v))))

(defn parse-ns
  "Returns tuples with the ns as the first element and
   a map of the aliases for the namespace as the second element
   in the same format as ns-aliases"
  [body]
  (let [ns-decl (read-ns-decl (PushbackReader. (java.io.StringReader. body)))
        aliases (->> ns-decl
                     (filter list?)
                     (some #(when (#{:require} (first %)) %))
                     rest
                     (filter #(contains? (into #{} %) :as))
                     (#(zipmap (map (partial get-alias nil) %)
                               (map first %))))]
    [(second ns-decl) aliases]))

(defn read+analyze [env reader]
  (let [eof (reify)]
    (loop [forms []
           asts []]
      (let [form (r/read reader nil eof)
            ast (ana.jvm/analyze form env)]
        (if (identical? form eof)
          [asts forms]
          (recur (conj forms form)
                 (conj asts ast)))))))

(def passes-for-refactor
  #{#'source-info})

(def scheduled-passes
  (schedule passes-for-refactor))

(defn- run-passes-for-refactor
  "Set of passes refactor needs"
  [ast]
  (scheduled-passes ast))

(defn- noop-macroexpand-1 [form]
  form)

(defn string-ast [string]
  (try
    (let [[ns aliases] (parse-ns string)
          env (if (and ns (find-ns ns)) (assoc e :ns ns) e)
          r+a (partial read+analyze env)]
      (if (.contains string "defrecord")
        (assoc-in (ana.jvm/analyze-ns ns) [0 :alias-info] aliases)
        (binding [ana.jvm/run-passes run-passes-for-refactor
                  ana/macroexpand-1 noop-macroexpand-1]
          (with-env (ana.jvm/global-env)
            (-> string
                rts/indexing-push-back-reader
                r+a
                first
                (assoc-in [0 :alias-info] aliases))))))
    (catch Exception e
      (println "error when building AST for" (first (parse-ns string)))
      (.printStackTrace e)
      [])))
