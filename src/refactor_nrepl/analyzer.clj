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
            [refactor-nrepl.config :as config]
            [refactor-nrepl.ns.tracker :as tracker]
            [clojure.string :as str]
            [cljs.analyzer :as cljs-ana]
            [cljs.util :as cljs-util]
            [cljs.compiler :as cljs-comp]
            [cljs.env :as cljs-env]
            [cider.nrepl.middleware.util.cljs :as cljs])
  (:import java.io.PushbackReader
           java.util.regex.Pattern))

;;; The structure here is {ns {content-hash ast}}
(def ^:private ast-cache (atom {}))

(defn get-alias [as v]
  (cond as                (first v)
        (= (first v) :as) (get-alias true (rest v))
        :else             (get-alias nil (rest v))))

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
                     (remove symbol?)
                     (filter #(contains? (set %) :as))
                     (#(zipmap (map (partial get-alias nil) %)
                               (map first %))))]
    [(second ns-decl) aliases]))

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

(comment
;; integration test: find-used-locals
;; myast is the parsed ast of com.example.five and com.example.five-cljs respectively

;; cljs
(->> (nth myast 3) :init :methods first :body :ret :bindings first :children)
;; returns nil, but ... :init :form) returns (trim p), strangely :init has the same children as in the clj case

;; clj
(->> (nth myast 3) :init :expr :methods first :body :bindings first :children)
;; returns [:init] .. :init :form) returns (trim p)

  )

(defn- repair-binding-children
  "Repairs cljs AST by adding `:children` entries to `:binding` AST nodes, see above comment tag."
  []
  (fn [env ast opts]
    (if (= :let (:op ast))
      (update
       ast
       :bindings
       (fn [bindings]
         (mapv #(assoc % :children [:init]) bindings)))
      ast)))

(defn cljs-analyze-ns
  "Returns a sequence of abstract syntax trees for each form in
  the namespace."
  [ns]
  (cljs-env/ensure
    (let [f (cljs-util/ns->relpath ns)
          res (if (re-find #"^file://" f) (java.net.URL. f) (io/resource f))]
      (assert res (str "Can't find " f " in classpath"))
      (binding [cljs-ana/*cljs-ns* 'cljs.user
                cljs-ana/*cljs-file* (.getPath ^java.net.URL res)
                cljs-ana/*passes* [cljs-ana/infer-type cljs-ana/check-invoke-arg-types cljs-ana/ns-side-effects (repair-binding-children)]]
        (with-open [r (io/reader res)]
          (let [env (cljs-ana/empty-env)
                pbr (clojure.lang.LineNumberingPushbackReader. r)
                eof (Object.)]
            (loop [asts []
                   r (read pbr false eof false)]
              (let [env (assoc env :ns (cljs-ana/get-namespace cljs-ana/*cljs-ns*))]
                (if-not (identical? eof r)
                  (recur (conj asts (cljs-ana/analyze env r)) (read pbr false eof false))
                  asts)))))))))

(defn cljs-analyze-form [form]
  (cljs-env/ensure
   (binding [cljs-ana/*cljs-ns* 'cljs.user]
     (cljs-ana/analyze (cljs-ana/empty-env) form))))

(defn build-cljs-ast
  [file-content]
  (let [[ns aliases] (parse-ns file-content)]
    (assoc-in (cljs-analyze-ns ns) [0 :alias-info] aliases)))

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
         (<= line loc-line end-line)
         (<= column loc-column end-column))))

(defn normalize-anon-fn-params
  "replaces anon fn params in a read form"
  [form]
  (walk/postwalk
   (fn [token] (cond (re-matches #"p\d+__\d+#" (str token)) 'p
                     (instance? java.util.regex.Pattern token) (str token)
                     :default token)) form))

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
      ;; (println "raw-forms" (:raw-forms node))
      ;; (println "form "  (-> (:form node)
      ;;                       read-when-sexp
      ;;                       normalize-anon-fn-params))
      (if-let [forms (:raw-forms node)]
        (some #(re-find pattern %)
              (map (comp str normalize-anon-fn-params read-when-sexp) forms))
        (= sexp-sans-comments-and-meta (-> (:form node)
                                           read-when-sexp
                                           normalize-anon-fn-params))))))

(defn node-for-sexp-cljs?
  "Is NODE the ast node for SEXP for cljs?

  As `:raw-forms` (stages of macro expansion, including the original form) is not available in cljs AST it does the comparison the other way around. Eg parses `sexp` with the cljs parser and compares that with the `:form` of the AST node."
  [sexp node]
  (binding [*read-eval* false]
    (let [sexp-sans-comments-and-meta-form (:form (cljs-analyze-form (normalize-anon-fn-params (read-string sexp))))
          node-form                        (-> (:form node)
                                               read-when-sexp
                                               normalize-anon-fn-params)]
      ;; (println "sexp-sans-comments-and-meta" sexp-sans-comments-and-meta-form "types" (map type sexp-sans-comments-and-meta-form))
      ;; (println "form "  node-form "types" (map type node-form))
      (= sexp-sans-comments-and-meta-form node-form))))

(defn top-level-form-index
  [line column ns-ast]
  (->> ns-ast
       (map-indexed #(vector %1 (->> %2
                                     nodes
                                     (some (partial node-at-loc? line column)))))
       (filter #(second %))
       ffirst))

(defn node-at-loc-cljs?
  "Works around the fact that cljs AST nodes don't have end-line and end-column info in them. This cheat only works for top level forms because after a `clojure.tools.analyzer.ast/nodes` call we can't expect the nodes in the right order."
  [^long loc-line ^long loc-column node next-node]
  (let [{:keys [^long line ^long column]} (:env node)
        env-next-node                     (:env next-node)
        ^long end-column                  (:column env-next-node)
        ^long end-line                    (:line env-next-node)]
    ;; The node for ::an-ns-alias/foo, when it appeared as a toplevel form,
    ;; had nil as position info
    (and line end-line column end-column
         (or (< line loc-line end-line)
             (and (or (= line loc-line)
                      (= end-line loc-line))
                  (<= column loc-column end-column))))))

(defn top-level-form-index-cljs
  [line column ns-ast]
  (loop [[top-level-ast & top-level-asts-rest] ns-ast
         index 0]
    (if (or (node-at-loc-cljs? line column top-level-ast (first top-level-asts-rest))
            (not (first top-level-asts-rest)))
      index
      (recur top-level-asts-rest (inc index)))))
