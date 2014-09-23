(ns refactor-nrepl.analyzer
  (:refer-clojure :exclude [macroexpand-1 read read-string])
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.analyzer.env :refer [with-env]]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.passes.jvm
             [box :refer [box]]
             [constant-lifter :refer [constant-lift]]
             [annotate-branch :refer [annotate-branch]]
             [annotate-loops :refer [annotate-loops]]
             [annotate-methods :refer [annotate-methods]]
             [annotate-class-id :refer [annotate-class-id]]
             [annotate-internal-name :refer [annotate-internal-name]]
             [fix-case-test :refer [fix-case-test]]
             [clear-locals :refer [clear-locals]]
             [classify-invoke :refer [classify-invoke]]
             [infer-tag :refer [infer-tag ensure-tag]]
             [annotate-tag :refer [annotate-tag]]
             [validate-loop-locals :refer [validate-loop-locals]]
             [analyze-host-expr :refer [analyze-host-expr]]]
            [clojure.tools.analyzer.passes
             [source-info :refer [source-info]]
             [cleanup :refer [cleanup]]
             [elide-meta :refer [elide-meta]]
             [warn-earmuff :refer [warn-earmuff]]
             [add-binding-atom :refer [add-binding-atom]]
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

(defn ^:dynamic run-passes
  "Passes were copied from tools.analyzer.jvm and tweaked for the needs of
   refactor-nrepl

   Applies the following passes in the correct order to the AST:
   * uniquify
   * add-binding-atom
   * cleanup
   * source-info
   * elide-meta
   * warn-earmuff
   * jvm.box
   * jvm.constant-lifter
   * jvm.annotate-branch
   * jvm.annotate-loops
   * jvm.annotate-class-id
   * jvm.annotate-internal-name
   * jvm.annotate-methods
   * jvm.fix-case-test
   * jvm.clear-locals
   * jvm.classify-invoke
   * jvm.infer-tag
   * jvm.annotate-tag
   * jvm.validate-loop-locals
   * jvm.analyze-host-expr"
  [ast]
  (-> ast

      uniquify-locals
      add-binding-atom

      (prewalk (fn [ast]
                 (-> ast
                     warn-earmuff
                     source-info
                     elide-meta
                     annotate-methods
                     fix-case-test
                     annotate-class-id
                     annotate-internal-name)))

      ((fn analyze [ast]
         (postwalk ast
                   (fn [ast]
                     (-> ast
                         annotate-tag
                         analyze-host-expr
                         infer-tag
                         classify-invoke
                         constant-lift
                         (validate-loop-locals analyze))))))

      (prewalk (fn [ast]
                 (-> ast
                     box
                     annotate-loops ;; needed for clear-locals to safely clear locals in a loop
                     annotate-branch ;; needed for clear-locals
                     ensure-tag)))

      ;; needs to be run after collect-closed-overs
      clear-locals))

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

(defn string-ast [string]
  (try
    (let [[ns aliases] (parse-ns string)
          env (if (and ns (find-ns ns)) (assoc e :ns ns) e)
          r+a (partial read+analyze env)]
      (if (.contains string "defrecord")
        (assoc-in (ana.jvm/analyze-ns ns) [0 :alias-info] aliases)
        (binding [ana.jvm/run-passes run-passes]
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
