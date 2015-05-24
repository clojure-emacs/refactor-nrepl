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

(defn- fns-invoked?
  "Checks if `node` is a function-call present in `fn-set`."
  [fn-set alias-info node]
  (and (= :invoke (:op node))
       (fn-set (node->var alias-info (:fn node)))))

(defn- contains-var?
  "Checks if the var of `node` is present in the `var-set`."
  [var-set alias-info node]
  (var-set (node->var alias-info node)))

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
  (find-nodes ast
              #(fns-invoked? (into #{} (str/split fn-names #","))
                             (util/alias-info ast)
                             %)))

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

(defn- match [file-content line end-line]
  (let [line-index (dec line)
        eline (if (number? end-line) end-line line)]
    (->> file-content
         str/split-lines
         (drop line-index)
         (take (- eline line-index))
         (str/join "\n")
         str/trim)))

(defn- find-symbol-in-file [fully-qualified-name file]
  (let [file-content (slurp file)
        locs (->> (ns-ast file-content)
                  (find-symbol-in-ast fully-qualified-name)
                  (filter first))
        gather (fn [info]
                 (conj info
                       (.getCanonicalPath file)
                       (match file-content
                         (first info)
                         (second info))))]
    (when (seq locs) (map gather locs))))

(defn- find-global-symbol [file ns var-name clj-dir]
  (let [dir (or clj-dir ".")
        namespace (or ns (util/ns-from-string (slurp file)))
        fully-qualified-name (if (= namespace "clojure.core")
                               var-name
                               (str/join "/" [namespace var-name]))]
    (->> (util/list-project-clj-files dir)
         (mapcat (partial find-symbol-in-file fully-qualified-name))
         (map identity))))

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
            local-var-name (->> top-level-form-ast
                                nodes
                                (filter #(and (#{:local :binding} (:op %))
                                              (= var-name (-> % :form str))
                                              (:local %)))
                                (filter (partial util/node-at-loc? line column))
                                first
                                :name)]
        (map #(conj (vec (take 4 %))
                    var-name
                    (.getCanonicalPath (java.io.File. file))
                    (match file-content
                      (first %)
                      (second %)))
             (find-nodes [top-level-form-ast]
                         #(and (#{:local :binding} (:op %))
                               (= local-var-name (-> % :name))
                               (:local %))))))))

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
