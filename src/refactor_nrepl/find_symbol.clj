(ns refactor-nrepl.find-symbol
  (:require [cider.nrepl.middleware.util.misc :refer [err-info]]
            [clojure.string :as str]
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

(defn- find-symbol-in-ast [name ast]
  (when ast
    (find-nodes ast (partial contains-var-or-const? name (alias-info ast)))))

(defn- find-debug-fns-reply [{:keys [transport ns-string debug-fns] :as msg}]
  (try
    (let [ast (ns-ast ns-string)
             result (find-invokes ast debug-fns)]
         (transport/send transport
                         (response-for msg :value (when (not-empty result) result)
                                       :status :done)))
    (catch IllegalStateException e
      (response-for msg :error (.getMessage e) :status :done))
    (catch Exception e
      (transport/send transport
                      (response-for msg (err-info e :find-debug-fns-error))))))

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
                  (find-symbol-in-ast fully-qualified-name)
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
  (let [ns-string (slurp file)
        ast (ns-ast ns-string)]
    (when-let [form-index (top-level-form-index line column ast)]
      (let [top-level-form-ast (nth ast form-index)
            local-var-name (->> top-level-form-ast
                                nodes
                                (filter #(and (#{:local :binding} (:op %)) (= var-name (-> % :form str)) (:local %)))
                                (filter (partial node-at-loc? line column))
                                first
                                :name)]
        (->> (find-nodes [top-level-form-ast] #(and (#{:local :binding} (:op %)) (= local-var-name (-> % :name)) (:local %)))
             (map #(conj (vec (take 4 %)) var-name (.getCanonicalPath (java.io.File. file)) (match ns-string (first %) (second %)))))))))

(defn- find-symbol [{:keys [file ns name dir line column]}]
  (throw-unless-clj-file file)
  (or (when (and file (not-empty file)) (not-empty (find-local-symbol file name line column)))
      (find-global-symbol file ns name dir)))

(defn- create-result-alist
  [line-beg line-end col-beg col-end name file match]
  (list :line-beg line-beg
        :line-end line-end
        :col-beg col-beg
        :col-end col-end
        :name name
        :file file
        :match match))

(defn- find-symbol-reply [{:keys [transport] :as msg}]
  (try
    (let [occurrences (find-symbol msg)]
      (doseq [occurrence occurrences
              :let [response (pr-str (apply create-result-alist occurrence))]]
        (transport/send transport
                        (response-for msg :occurrence response)))
      (transport/send transport (response-for msg :count (count occurrences)
                                              :status :done)))
    (catch IllegalArgumentException e
      (transport/send transport
                      (response-for msg :error (.getMessage e) :status :done)))
    (catch IllegalStateException e
      (transport/send transport
                      (response-for msg :error (.getMessage e) :status :done)))
    (catch Exception e
      (transport/send transport
                      (response-for msg (err-info e :find-symbol-error))))))

(defn wrap-find-symbol
  [handler]
  (fn [{:keys [op ns-string] :as msg}]
    (cond (= op "find-debug-fns") (find-debug-fns-reply msg)
          (= op "find-symbol") (find-symbol-reply msg)
          :else
          (handler msg))))

(set-descriptor!
 #'wrap-find-symbol
 {:handles
  {"find-symbol"
   {:doc "Streams back information about each occurrence of symbol."
    :requires {"file" "The absolute path to the file containing the
    symbol to lookup."
               "dir" "Only files below this dir will be searched."
               "ns" "The ns where the symbol is defined."
               "name" "The name of the symbol"
               "line" "The line number where the symbol occurrs."
               "column" "The column number where the symbol occurs."}
    :returns {"status" "done"
              "count" "The total number of symbols found"
              "occurrence" "Information about a single occurrence:
              e.g.
(:line-beg 5 :line-end 5 :col-beg 19 :col-end 26
:name a-name :file \"/absolute/path/to/file.clj\"
:match (fn-name some args))"}
    "error" "An error message intended to be displayed to the user."}
   "find-debug-fns"
   {:doc "- find-debug-fns: finds debug functions returns tuples containing
    [line-number end-line-number column-number end-column-number fn-name]"}}})
