(ns refactor-nrepl.refactor
  (:require [refactor-nrepl.analyzer :refer [string-ast]]
            [refactor-nrepl.util :refer :all]
            [clojure.string :refer [split join] :as str]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]))

(defn- node->var [alias-info node]
  (let [class (or (:class node) (-> node
                                    :var
                                    str
                                    (str/replace "#'" "")
                                    (str/replace "clojure.core/" "")))
        full-class (get alias-info class class)]
    (join "/" (remove nil? [full-class (:field node)]))))

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
  (find-nodes ast (partial fns-invoked? (into #{} (split fn-names #",")) (alias-info ast))))

(defn- contains-var [var-name alias-info node]
  (contains-var? #{var-name} alias-info node))

(defn- contains-const? [var-name alias-info node]
  (let [[ns name] (str/split var-name #"/")]
    (and (= :const (:op node))
         (.contains (-> node :val str) ns)
         (or (not name)
             (.contains (-> node :val str) name))
         var-name)))

(defn- contains-var-or-const? [var-name alias-info node]
  (or (contains-var var-name alias-info node)
      (contains-const? var-name alias-info node)))

(defn- find-symbol [name ast]
  (when ast
    (find-nodes ast (partial contains-var-or-const? name (alias-info ast)))))

(defn- find-debug-fns-reply [{:keys [transport ns-string debug-fns] :as msg}]
  (let [ast (string-ast ns-string)
        result (find-invokes ast debug-fns)]
    (transport/send transport (response-for msg :value (when (not-empty result) result) :status :done))))

(defn- find-symbol-in-file [fully-qualified-name file]
  (let [file-content (slurp file)
        locs (->> file-content
                  string-ast
                  (find-symbol fully-qualified-name)
                  (filter #(first %)))]
    (when-not (empty? locs)
      (map #(conj % (.getCanonicalPath file) (->> file-content
                                                  str/split-lines
                                                  (drop (dec (first %)))
                                                  first))
           locs))))

(defn- find-symbol-reply [{:keys [transport ns-string ns name clj-dir] :as msg}]
  (let [dir (or clj-dir ".")
        namespace (or ns (ns-from-string ns-string))
        fully-qualified-name (if (= namespace "clojure.core")
                               name
                               (str/join "/" [namespace name]))
        syms (map identity (mapcat (partial find-symbol-in-file fully-qualified-name) (list-project-clj-files dir)))]
    (doseq [found-sym syms]
      (transport/send transport (response-for msg :occurrence found-sym)))
    (transport/send transport (response-for msg :syms-count (count syms) :status :done))))

(defn- find-referred-reply [{:keys [transport ns-string referred] :as msg}]
  (let [ast (string-ast ns-string)
        matches (find-nodes ast (partial contains-var referred (alias-info ast)))
        result (< 0 (count matches))]
    (transport/send transport (response-for msg :value (when result (str result)) :status :done))))

(defn wrap-refactor
  "Ensures that refactor only triggered with the right operation and forks to the appropriate refactor function"
  [handler]
  (fn [{:keys [op refactor-fn ns-string] :as msg}]
    (if (= "refactor" op)
      (cond (= "find-referred" refactor-fn) (find-referred-reply msg)
            (= "find-debug-fns" refactor-fn) (find-debug-fns-reply msg)
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
          - find-referred: searches for referred class in the AST returns the referred if found, nil otherwise
          - find-debug-fns: finds debug functions returns tuples containing
            [line-number end-line-number column-number end-column-number fn-name]
          - find-symbol: finds symbol in the project returns tuples containing
            [line-number end-line-number column-number end-column-number fn-name absolute-path the-matched-line]
            when done returns done status message a found symbols count"
    :requires {"ns-string" "the body of the namespace to build the AST with"
               "refactor-fn" "The refactor function to invoke"}
    :returns {"status" "done"
              "value" "result of the refactor"}}}})
