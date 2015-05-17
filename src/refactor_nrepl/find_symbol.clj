(ns refactor-nrepl.find-symbol
  (:require [clojure.string :as str]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [refactor-nrepl
             [analyzer :refer [ns-ast]]
             [util :as util]]))

(defn- node->var
  "Returns a fully qualified symbol for vars other those from clojure.core, for
  which the non-qualified name is returned."
  [alias-info node]
  (let [class (or (:class node)
                  (-> (str (:var node))
                      (str/replace "#'" "")
                      (str/replace "clojure.core/" "")))
        full-class (get alias-info class class)]
    (str/join "/" (remove nil? [full-class (:field node)]))))

(defn- contains-var?
  "Checks if the var of `node` is present in the `var-set`."
  [var-set alias-info node]
  (contains? var-set (node->var alias-info node)))

(defn- find-nodes
  "Filters `ast` with `pred` and returns a list of vectors with line-beg, line-end,
  colum-beg, column-end and the result of applying pred to the node for each
  node in the AST."
  [ast pred]
  (->> (mapcat nodes ast)
       (filter pred)
       (map (juxt (comp :line :env)
                  (comp :end-line :env)
                  (comp :column :env)
                  (comp :end-column :env)
                  pred))))

(defn- find-invokes
  "Finds fn invokes in the AST.
  Returns a list of line, end-line, column, end-column and fn name tuples"
  [ast fn-names]
  (let [fns (into #{} (str/split fn-names #","))
        fns-invoked? (every-pred
                      (comp (partial = :invoke) :op)
                      (partial contains-var?
                               fns
                               (util/alias-info ast)))]
    (find-nodes ast fns-invoked?)))

(def ^:private symbol-regex #"[\w\.:\*\+\-_!\?]+")

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
  (or (contains-var? #{var-name} alias-info node)
      (contains-const? var-name alias-info node)))

(defn- find-symbol-in-ast [name ast]
  (when ast
    (find-nodes ast
                (partial contains-var-or-const?
                         name
                         (util/alias-info ast)))))

(defn- find-context [file-content line end-line]
  (let [line-index (dec line)
        eline (if (number? end-line) end-line line)]
    (->> file-content
         str/split-lines
         (drop line-index)
         (take (- eline line-index))
         (str/join "\n")
         str/trim)))

(defn add-context
  [loc-info file content]
  (let [file (if (.isFile file) file (java.io.File. file))]
    (conj loc-info
          (.getCanonicalPath file)
          (find-context content
                        (first loc-info)
                        (second loc-info)))))

(defn- find-symbol-in-file [fully-qualified-name file]
  (let [file-content (slurp file)
        locs (->> (ns-ast file-content)
                  (find-symbol-in-ast fully-qualified-name)
                  (filter first))]
    (when (seq locs)
      (map #(add-context % file file-content)
           locs))))

(defn- find-global-symbol [file ns var-name clj-dir]
  (let [dir (or clj-dir ".")
        namespace (or ns (util/ns-from-string (slurp file)))
        fully-qualified-name (if (= namespace "clojure.core")
                               var-name
                               (str/join "/" [namespace var-name]))]
    (->> (util/list-project-clj-files dir)
         (mapcat (partial find-symbol-in-file fully-qualified-name))
         (map identity))))

(defn- local-node-match? [node var-name kw]
  (and (#{:local :binding} (:op node))
       (= var-name (str (get node kw)))
       (:local node)))

(defn- find-local-symbol
  "Find local symbol occurrences

  file is the file where the request is made
  var-name is the name of the var the user wants to know about
  line is the line number of the symbol
  column is the column of the symbol"
  [file var-name line column]
  {:pre [(number? line)
         (number? column)
         (not-empty file)]}
  (let [file-content (slurp file)
        ast (ns-ast file-content)]
    (when-let [form-index (util/top-level-form-index line column ast)]
      (let [top-level-form-ast (nth ast form-index)
            local-var-name (->> (nodes top-level-form-ast)
                                (filter #(local-node-match? % var-name :form))
                                (filter (partial util/node-at-loc? line column))
                                first
                                :name)]
        (map #(add-context (vec (take 4 %)) file file-content)
             (find-nodes [top-level-form-ast]
                         #(local-node-match? % local-var-name :name)))))))

(defn find-symbol [{:keys [file ns name dir line column]}]
  (util/throw-unless-clj-file file)
  (or (when (and file (not-empty file))
        (not-empty (find-local-symbol file name line column)))
      (find-global-symbol file ns name dir)))

(defn create-result-alist
  [line-beg line-end col-beg col-end name file match]
  (list :line-beg line-beg
        :line-end line-end
        :col-beg col-beg
        :col-end col-end
        :name name
        :file file
        :match match))

(defn find-debug-fns [{:keys [ns-string debug-fns]}]
  (let [res  (-> ns-string ns-ast (find-invokes debug-fns))]
    (when (seq res)
      res)))
