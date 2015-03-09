(ns refactor-nrepl.refactor
  (:require [clojure.string :as str]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl
             [analyzer :refer [ns-ast]]
             [util :refer :all]]))

(defn- node->var [alias-info node]
  (let [class (or (:class node) (-> node
                                    :var
                                    str
                                    (str/replace "#'" "")
                                    (str/replace "clojure.core/" "")))
        full-class (get alias-info class class)]
    (str/join "/" (remove nil? [full-class (:field node)]))))

(defn- fn-invoked [alias-info node]
  (node->var alias-info (:fn node)))

(defn- fns-invoked? [fns alias-info node]
  (and (= :invoke (:op node))
       (fns (fn-invoked alias-info node))))

(defn- contains-var? [var-name alias-info node]
  (var-name (node->var alias-info node)))

(defn- find-nodes [ast pred]
  (->> ast
       (map nodes)
       flatten
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
  (find-nodes ast (partial fns-invoked? (into #{} (str/split fn-names #",")) (alias-info ast))))

(defn- contains-var [var-name alias-info node]
  (contains-var? #{var-name} alias-info node))

(def ^:private symbol-regex #"[\w\.:\*\+\-_!\?]+")

(defn- contains-const? [var-name alias-info node]
  (let [[ns name] (str/split var-name #"/")
        const-node? (= :const (:op node))
        node-val-words (when const-node? (->> node
                                              :val
                                              str
                                              (re-seq symbol-regex)
                                              set))]
    (and const-node?
         (node-val-words ns)
         (or (not name)
             (node-val-words name))
         var-name)))

(defn- contains-var-or-const? [var-name alias-info node]
  (or (contains-var var-name alias-info node)
      (contains-const? var-name alias-info node)))

(defn- find-symbol [name ast]
  (when ast
    (find-nodes ast (partial contains-var-or-const? name (alias-info ast)))))

(defn- find-debug-fns-reply [{:keys [transport ns-string debug-fns] :as msg}]
  (try
    (let [ast (ns-ast ns-string)
             result (find-invokes ast debug-fns)]
         (transport/send transport
                         (response-for msg :value (when (not-empty result) result)
                                       :status :done)))
    (catch Exception e
      (transport/send transport
                      (response-for msg :error (.getMessage e) :status :done)))))

(defn- match [file-content line end-line]
  (let [line-index (dec line)
        eline (if (number? end-line) end-line line)]
    (->> file-content
         str/split-lines
         (drop line-index)
         (take (- eline line-index))
         (str/join "\n"))))

(defn- find-symbol-in-file [fully-qualified-name file]
  (let [file-content (slurp file)
        locs (->> file-content
                  ns-ast
                  (find-symbol fully-qualified-name)
                  (filter #(first %)))]
    (when-not (empty? locs)
      (map #(conj % (.getCanonicalPath file) (match file-content (first %) (second %)))
           locs))))

(defn- find-global-symbol [file ns var-name clj-dir]
  (let [dir (or clj-dir ".")
        namespace (or ns (ns-from-string (slurp file)))
        fully-qualified-name (if (= namespace "clojure.core")
                               var-name
                               (str/join "/" [namespace var-name]))]
    (->> dir
         list-project-clj-files
         (mapcat (partial find-symbol-in-file fully-qualified-name))
         (map identity))))

(defn- node-at-loc? [loc-line loc-column node]
  (let [env (:env node)]
    (and (= loc-line (:line env))
         (>= loc-column (:column env))
         (<= loc-column (:end-column env)))))

(defn- find-local-symbol
  "Find local symbol occurrences

file is the file where the request is made
var-name is the name of the var the user wants to know about
line is the line number of the occurrences
column is the column of the occurrence"
  [file var-name line column]
  {:pre [(number? line)
         (number? column)
         (not-empty file)]}
  (let [ns-string (slurp file)
        ast (ns-ast ns-string)]
    (when-let [form-index (->> ast
                               (map-indexed #(vector %1 (->> %2
                                                             nodes
                                                             (some (partial node-at-loc? line column)))))
                               (filter #(second %))
                               ffirst)]
      (let [top-level-form-ast (nth ast form-index)
            local-var-name (->> top-level-form-ast
                                nodes
                                (filter #(and (#{:local :binding} (:op %)) (= var-name (-> % :form str)) (:local %)))
                                (filter (partial node-at-loc? line column))
                                first
                                :name)]
        (->> (find-nodes [top-level-form-ast] #(and (#{:local :binding} (:op %)) (= local-var-name (-> % :name)) (:local %)))
             (map #(conj (vec (take 4 %)) var-name (.getCanonicalPath (java.io.File. file)) (match ns-string (first %) (second %)))))))))

(defn- find-symbol-reply [{:keys [transport file ns name clj-dir loc-line loc-column] :as msg}]
  (try
    (let [syms (or (when (and file (not-empty file)) (not-empty (find-local-symbol file name loc-line loc-column)))
                      (find-global-symbol file ns name clj-dir))]
      (doseq [found-sym syms]
        (transport/send transport (response-for msg :occurrence found-sym)))
      (transport/send transport (response-for msg :syms-count (count syms)
                                              :status :done)))
    (catch Exception e
      (transport/send transport
                      (response-for msg :error (.getMessage e) :status :done)))))

(defn wrap-refactor
  "Ensures that refactor only triggered with the right operation and
  forks to the appropriate refactor function"
  [handler]
  (fn [{:keys [op refactor-fn ns-string] :as msg}]
    (if (= "refactor" op)
      (cond (= "find-debug-fns" refactor-fn) (find-debug-fns-reply msg)
            (= "find-symbol" refactor-fn) (find-symbol-reply msg)
            :else
            (handler msg))
      (handler msg))))

(set-descriptor!
 #'wrap-refactor
 {:handles
  {"refactor"
   {:doc "Returns a an appropriate result for the given refactor fn.
          Currently available:
          - find-debug-fns: finds debug functions returns tuples containing
            [line-number end-line-number column-number end-column-number fn-name]
          - find-symbol: finds symbol in the project returns tuples containing
            [line-number end-line-number column-number end-column-number fn-name absolute-path the-matched-line]"
    :requires {"ns-string" "the body of the namespace to build the AST with"
               "refactor-fn" "The refactor function to invoke"}
    :returns {"status" "done"
              "value" "result of the refactor"}}}})
