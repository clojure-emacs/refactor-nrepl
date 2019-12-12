(ns refactor-nrepl.find.find-symbol
  (:require [clojure
             [set :as set]
             [string :as str]]
            [clojure.tools.analyzer.ast :refer [nodes postwalk]]
            [clojure.tools.namespace.parse :as parse]
            [refactor-nrepl
             [analyzer :as ana]
             [core :as core]
             [s-expressions :as sexp]]
            [refactor-nrepl.find.find-macros :refer [find-macro]]
            [refactor-nrepl.find.util :as find-util]
            [refactor-nrepl.ns.libspecs :as libspecs])
  (:import (java.io File)))

(def ^:private symbol-regex #"[\w\.:\*\+\-_!\?]+")

(defn- node->var
  "Returns a fully qualified symbol for vars other those from clojure.core, for
  which the non-qualified name is returned."
  [alias-info node]
  (let [class (or (:class node)
                  (-> (str (:var node))
                      (str/replace "#'" "")
                      (str/replace "clojure.core/" "")))
        full-class (get alias-info class class)]
    (str/join "/" (remove nil? [(if (map? full-class) "" full-class) (:field node)]))))

(defn- contains-var?
  "Checks if the var of `node` is same as given `var-name`"
  [var-name alias-info node]
  (= var-name (node->var alias-info node)))

(defn present-before-expansion?
  "returns true if node is not result of macro expansion or if it is and it contains
  the not qualified var-name before expansion"
  [var-name node]
  (if-let [orig-form (-> node :raw-forms first str not-empty)]
    (re-find (re-pattern (str "(^|\\W)" (last (str/split var-name #"/")) "\\W")) orig-form)
    true))

(defn- dissoc-macro-nodes
  "Strips those macro nodes from the ast node which don't contain name before expansion"
  [name node]
  (if (present-before-expansion? name node)
    node
    (apply dissoc node (:children node))))

(defn- find-nodes
  "Filters `ast` with `pred` and returns a list of vectors with line-beg, line-end,
  colum-beg, column-end for each node in the AST.

  if name present macro call sites are checked if they contained name before macro expansion"
  ([asts pred]
   (->> (mapcat nodes asts)
        (filter pred)
        (map (juxt (comp :line :env)
                   (comp :end-line :env)
                   (comp :column :env)
                   (comp :end-column :env)))
        (map #(zipmap [:line-beg :line-end :col-beg :col-end] %))))
  ([name asts pred]
   (find-nodes (map #(postwalk % (partial dissoc-macro-nodes name)) asts) pred)))

(defn- alias-info [full-ast]
  (-> full-ast first :alias-info))

(defn- contains-const?
  [var-name alias-info node]
  (let [[ns name] (str/split var-name #"/")
        const-node? (= :const (:op node))
        node-val-words (when const-node?
                         (->> (str (:val node))
                              (re-seq symbol-regex)
                              set))]
    (and const-node?
         (node-val-words ns)
         (or (not name) (node-val-words name))
         var-name)))

(defn- contains-var-or-const? [var-name alias-info node]
  (or (contains-var? var-name alias-info node)
      (contains-const? var-name alias-info node)))

(defn- find-symbol-in-ast [name asts]
  (when asts
    (find-nodes name
                (remove (fn [ast] (-> ast :raw-forms first parse/ns-decl?))
                        asts)
                (partial contains-var-or-const?
                         name
                         (alias-info asts)))))

(defn- match [file-content ^long line ^long end-line]
  (let [line-index (dec line)
        eline (if (number? end-line) end-line line)]
    (->> file-content
         str/split-lines
         (drop line-index)
         (take (- eline line-index))
         (str/join "\n")
         str/trim)))

(defn- find-symbol-in-file [fully-qualified-name ignore-errors referred-syms ^File file]
  (let [file-content (slurp file)
        locs (try (->> (ana/ns-ast file-content)
                       (find-symbol-in-ast fully-qualified-name)
                       (filter :line-beg))
                  (catch Exception e
                    (when-not ignore-errors
                      (throw e))))
        locs (into
              locs (some->
                    referred-syms
                    (get-in [:clj (str file) fully-qualified-name])
                    meta
                    ((fn [{:keys [line column end-line end-column]}]
                       (list {:line-beg line
                              :line-end end-line
                              :col-beg column
                              :col-end end-column})))))
        gather (fn [info]
                 (merge info
                        {:file (.getCanonicalPath file)
                         :name fully-qualified-name
                         :match (match file-content
                                  (:line-beg info)
                                  (:line-end  info))}))]
    (map gather locs)))

(defn- find-global-symbol [file ns var-name ignore-errors]
  (let [namespace (or ns (core/ns-from-string (slurp file)))
        fully-qualified-name (if (= namespace "clojure.core")
                               var-name
                               (str/join "/" [namespace var-name]))
        referred-syms (libspecs/referred-syms-by-file&fullname)]
    (->> (core/dirs-on-classpath)
         (mapcat (partial core/find-in-dir (some-fn core/clj-file? core/cljc-file?)))
         (mapcat (partial find-symbol-in-file fully-qualified-name ignore-errors referred-syms)))))

(defn- get&read-enclosing-sexps
  [file-content {:keys [^long line-beg ^long col-beg]}]
  (binding [*read-eval* false]
    (let [line (dec line-beg)
          encl-sexp-level1 (or (sexp/get-enclosing-sexp file-content line col-beg) "")
          encl-sexp-level2 (or (sexp/get-enclosing-sexp file-content line col-beg 2) "")]
      [encl-sexp-level1 (read-string encl-sexp-level1)
       encl-sexp-level2 (read-string encl-sexp-level2)])))

(defn- optmap-with-default?
  [var-name file-content [_ [_ level1-form _ level2-form]]]
  (and (vector? level1-form)
       (map? level2-form)
       (= #{:or :keys} (set/intersection #{:or :keys} (set (keys level2-form))))
       (some #{var-name} (map str (keys (:or level2-form))))))

(defn- re-pos
  "Map of regexp matches and their positions keyed by positions."
  [re s]
  (loop [m (re-matcher re s)
         res (sorted-map)]
    (if (.find m)
      (recur m (assoc res (.start m) (.group m)))
      res)))

(defn- occurrence-for-optmap-default
  [var-name [orig-occurrence [_ _ ^String level2-string _]]]
  (let [var-positions (re-pos (re-pattern (format "\\W%s\\W" var-name)) level2-string)
        ^long var-default-pos (first (second var-positions))
        newline-cnt (reduce (fn [cnt char] (if (= char \newline) (inc (long cnt)) cnt)) 0 (.substring level2-string 0 var-default-pos))
        prev-newline-position (->> (concat (keys (re-pos #"\n" level2-string))
                                           (keys var-positions))
                                   sort
                                   (take-while (partial not= var-default-pos))
                                   last)
        new-col (if (= 0 newline-cnt)
                  (- var-default-pos (long (ffirst var-positions)))
                  (inc (- (long var-default-pos) (long prev-newline-position))))
        new-occurrence (-> (update-in orig-occurrence [:line-beg] + newline-cnt)
                           (update-in [:line-end] + newline-cnt))]
    (if (= 0 newline-cnt)
      (-> (update-in new-occurrence [:col-beg] + new-col)
          (update-in [:col-end] + new-col))
      (-> (assoc new-occurrence :col-beg new-col)
          (assoc :col-end (+ new-col (count var-name)))
          (assoc :match (nth (str/split-lines level2-string) newline-cnt))))))

(defn- find-local-symbol
  "Find local symbol occurrences

  file is the file where the request is made
  var-name is the name of the var the user wants to know about
  line is the line number of the symbol
  column is the column of the symbol"
  [^String file var-name line column]
  {:pre [(number? line)
         (number? column)
         (not-empty file)]}
  (let [file-content (slurp file)
        ast (ana/ns-ast file-content)]
    (when-let [form-index (ana/top-level-form-index line column ast)]
      (let [top-level-form-ast (nth ast form-index)
            local-var-name (->> top-level-form-ast
                                nodes
                                (filter #(and (#{:local :binding} (:op %))
                                              (= var-name (-> % :form str))
                                              (:local %)))
                                (filter (partial ana/node-at-loc? line column))
                                first
                                :name)
            local-occurrences
            (map #(merge %
                         {:name var-name
                          :file (.getCanonicalPath (java.io.File. file))
                          :match (match file-content
                                   (:line-beg %)
                                   (:line-end %))})
                 (find-nodes var-name
                             [top-level-form-ast]
                             #(and (#{:local :binding} (:op %))
                                   (= local-var-name (-> % :name))
                                   (:local %))))
            optmap-def-occurrences
            (->> local-occurrences
                 (map (juxt identity (partial get&read-enclosing-sexps file-content)))
                 (filter (partial optmap-with-default? var-name file-content))
                 (map (partial occurrence-for-optmap-default var-name)))]
        (sort-by :line-beg (concat local-occurrences optmap-def-occurrences))))))

(defn find-symbol [{:keys [file ns name line column ignore-errors]}]
  (core/throw-unless-clj-file file)
  (let [ignore-errors? (= ignore-errors "true")
        macros (future (find-macro (core/fully-qualify ns name) ignore-errors?))
        globals (->> (find-global-symbol file ns name ignore-errors?)
                     distinct
                     (remove find-util/spurious?)
                     future)]

    (or
     ;; find-local-symbol is the fastest of the three
     ;; if result is not empty, there is no point in keeping `find-macro` and `find-global-symbol` futures still active
     (when-let [result (not-empty (remove find-util/spurious? (distinct (find-local-symbol file name line column))))]
       (future-cancel macros)
       (future-cancel globals)
       result)

     ;; find-macros has to be checked first because find-global-symbol
     ;; can return spurious hits for some macro definitions
     @macros
     @globals)))
