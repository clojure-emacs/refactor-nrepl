(ns refactor-nrepl.analyzer
  (:refer-clojure :exclude [macroexpand-1])
  (:require [clojure.java.io :as io]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.reader :as reader]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [clojure.tools.analyzer.jvm :as aj]
            [clojure.tools.analyzer.jvm.utils :as ajutils]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.walk :as walk]
            [refactor-nrepl
             [config :as config]]
            [refactor-nrepl.ns.tracker :as tracker]
            [clojure.string :as str])
  (:import java.io.PushbackReader
           java.util.regex.Pattern))

;;; The structure here is {ns {content-hash ast}}
(def ^:private ast-cache (atom {}))

(defn get-alias [as v]
  (cond as (first v)
        (= (first v) :as) (get-alias true (rest v))
        :else (get-alias nil (rest v))))

(defn parse-ns
  "Returns tuples with the ns as the first element and
  a map of the aliases for the namespace as the second element
  in the same format as ns-aliases"
  [body]
  (with-open [string-reader (java.io.StringReader. body)]
    (let [ns-decl (read-ns-decl (PushbackReader. string-reader))
          aliases (->> ns-decl
                       (filter list?)
                       (some #(when (#{:require} (first %)) %))
                       rest
                       (remove symbol?)
                       (filter #(contains? (set %) :as))
                       (#(zipmap (map (partial get-alias nil) %)
                                 (map first %))))]
      [(second ns-decl) aliases])))

(defn- noop-macroexpand-1 [form]
  form)

(defn- get-ast-from-cache
  [ns file-content]
  (-> @ast-cache
      (get ns)
      (get (hash file-content))))

(defn- update-ast-cache
  [file-content ns ast]
  (swap! ast-cache assoc ns {(hash file-content) ast})
  ast)

(defn- ns-on-cp? [ns]
  (io/resource (ajutils/ns->relpath ns)))

(defn- shadow-unresolvable-symbol-handler [symbol-ns symbol-name symbol-ast]
  {:op :const
   :form (:form symbol-ast)
   :literal? true
   :type :string
   :val (if symbol-ns
          (str symbol-ns "/" symbol-name)
          symbol-name)
   :children []})

(defn- shadow-wrong-tag-handler [tag-key origination-ast]
  nil)

(defn- build-ast
  [ns aliases]
  (when (ns-on-cp? ns)
    (let [opts {:passes-opts
                {:validate/unresolvable-symbol-handler shadow-unresolvable-symbol-handler
                 :validate/throw-on-arity-mismatch false
                 :validate/wrong-tag-handler shadow-wrong-tag-handler}}]
      (binding [ana/macroexpand-1 noop-macroexpand-1
                reader/*data-readers* *data-readers*]
        (assoc-in (aj/analyze-ns ns (aj/empty-env) opts) [0 :alias-info] aliases)))))

(defn- cachable-ast [file-content]
  (let [[ns aliases] (parse-ns file-content)]
    (when ns
      (if-let [cached-ast-or-err (get-ast-from-cache ns file-content)]
        cached-ast-or-err
        (when-let [new-ast-or-err (try (build-ast ns aliases) (catch Throwable th th))]
          (update-ast-cache file-content ns new-ast-or-err))))))

(defn- throw-ast-in-bad-state
  [file-content msg]
  (throw (IllegalStateException.
          (str "refactor-nrepl is unable to build an AST for "
               (first (parse-ns file-content))
               ". tools.analyzer encountered the following problem: " msg))))

(defn ns-ast
  "Build AST for a namespace"
  [file-content]
  (let [ast-or-err (cachable-ast file-content)
        error? (instance? Throwable ast-or-err)
        debug (:debug config/*config*)]

    (cond
      (and error? debug)
      (throw ast-or-err)

      error?
      (throw-ast-in-bad-state file-content (.getMessage ^Throwable ast-or-err))

      :default
      ast-or-err)))

(defn- ast-stats []
  (let [asts @ast-cache]
    (interleave (keys asts)
                (->> (vals asts)
                     (mapcat vals)
                     (map #(if (instance? Throwable %)
                             (list "error" (.getMessage ^Throwable %))
                             "OK"))))))

(defn warm-ast-cache []
  (doseq [f (tracker/project-files-in-topo-order)]
    (try
      (ns-ast (slurp f))
      (catch Throwable th))) ;noop, ast-status will be reported separately
  (ast-stats))

(defn node-at-loc? [^long loc-line ^long loc-column node]
  (let [{:keys [^long line ^long end-line ^long column ^long end-column]} (:env node)]
    ;; The node for ::an-ns-alias/foo, when it appeared as a toplevel form,
    ;; had nil as position info
    (and line end-line column end-column
         (and (>= loc-line line)
              (<= loc-line end-line)
              (>= loc-column column)
              (<= loc-column end-column)))))

(defn- normalize-anon-fn-params
  "replaces anon fn params in a read form"
  [form]
  (walk/postwalk
   (fn [token] (if (re-matches #"p\d+__\d+#" (str token)) 'p token)) form))

(defn- read-when-sexp [form]
  (let [f-string (str form)]
    (when (some #{\) \} \]} f-string)
      (read-string f-string))))

(defn node-for-sexp?
  "Is NODE the ast node for SEXP?"
  [sexp node]
  (binding [*read-eval* false]
    (let [sexp-sans-comments-and-meta (normalize-anon-fn-params (read-string sexp))
          pattern (re-pattern (Pattern/quote (str sexp-sans-comments-and-meta)))]
      (if-let [forms (:raw-forms node)]
        (some #(re-find pattern %)
              (map (comp str normalize-anon-fn-params read-when-sexp) forms))
        (= sexp-sans-comments-and-meta (-> (:form node)
                                           read-when-sexp
                                           normalize-anon-fn-params))))))

(defn top-level-form-index
  [line column ns-ast]
  (->> ns-ast
       (map-indexed #(vector %1 (->> %2
                                     nodes
                                     (some (partial node-at-loc? line column)))))
       (filter #(second %))
       ffirst))
