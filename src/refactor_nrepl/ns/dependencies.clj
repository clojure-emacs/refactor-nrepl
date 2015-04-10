(ns refactor-nrepl.ns.dependencies
  (:require [cider.nrepl.middleware.info :refer [info-clj]]
            [clojure
             [string :as str]
             [walk :as walk]]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [refactor-nrepl
             [analyzer :refer [ns-ast]]
             [util :refer [ns-from-string]]]
            [refactor-nrepl.ns
             [helpers :refer [file-content-sans-ns prefix suffix]]
             [ns-parser :refer [get-imports get-libspecs]]])
  (:import [java.io PushbackReader StringReader]))

(defn- get-class-name [{:keys [op class type val] :as node}]
  (when-let [c (if (and (= :const op)
                        (= :class type))
                 val
                 ;; catches static-invoke/static-field
                 (when (class? class)
                   class))]
    {:name (.getName ^Class c)}))

(defn- get-var-alias [node var-name]
  (let [alias? (some->>
                node
                :form
                str
                (re-matches
                 (re-pattern (str "[A-Za-z0-9_.?$%!-]+/" (suffix var-name)))))]
    (when (not= alias? var-name) alias?)))

(defn- get-var-name-and-alias [node]
  (when-let [var-name (some-> node :var str (str/replace "#'" ""))]
    {:name var-name
     :alias (get-var-alias node var-name)}))

(defn- get-interface-name [{:keys [op interface]}]
  (when (and interface
             (= op :method))
    {:name (.getName ^Class interface)}))

(defn- normalize-name [n]
  (cond
    (.endsWith n ".") (recur (.substring n 0 (dec (.length n))))
    :else n))

(defn- get-symbol-literal [node]
  (when (and (:literal? node) (= (:type node) :symbol))
    {:name (some-> node :form str normalize-name)}))

(defn- node->name-and-alias [node]
  (or (get-class-name node)
      (get-var-name-and-alias node)
      (get-symbol-literal node)
      (get-interface-name node)))

(defn- used-vars
  "Finds used functions and classes"
  [ast]
  (->> ast
       (map nodes)
       flatten
       (map node->name-and-alias)
       (remove #(str/blank? (:name %)))))

(defn- ns-in-use?
  [ns used-syms]
  (some #(.startsWith (str %) (str ns)) (map :name used-syms)))

(defn- referred-symbol-in-use?
  [used-syms sym]
  (some (fn [{:keys [name alias]}]
          (and (= (symbol (suffix name)) sym)
               (not alias)))
        used-syms))

(defn- prune-refer
  [refer-clause used-syms]
  (filter (partial referred-symbol-in-use? used-syms) refer-clause))

(defn- remove-unused
  [used-syms {:keys [ns refer as] :as libspec}]
  (when (ns-in-use? ns used-syms)
    (if (= refer :all)
      libspec
      (update-in libspec [:refer] prune-refer used-syms))))

(defn remove-empty-refer
  [{:keys [refer] :as libspec}]
  (if (and (not= refer :all) (empty? refer))
    (dissoc libspec :refer)
    libspec))

(defn- remove-unused-syms-and-specs
  [used-syms libspec]
  (some->> libspec
           (remove-unused used-syms)
           remove-empty-refer))

(defn- remove-unused-imports
  [symbols-in-use imports]
  (filter (set (map :name symbols-in-use)) imports))

(defn- macro? [ns sym]
  (:macro (info-clj ns sym)))

(defn- add-prefix
  [prefix var]
  (symbol (str prefix "/" var)))

(defn- get-referred-symbols
  [libspec]
  (when-not (or (symbol? libspec)
                (= (:refer libspec) :all))
    (:refer libspec)))

(defn- find-fully-qualified-macros [file-content]
  (let [rdr (PushbackReader. (StringReader. (file-content-sans-ns file-content)))
        ns (ns-from-string file-content)
        syms (transient [])
        conj-symbol (fn [form] (when (symbol? form) (conj! syms form)))]
    (loop [form (read rdr nil :eof)]
      (when (not= form :eof)
        (walk/postwalk conj-symbol form)
        (recur (read rdr nil :eof))))
    (filter (partial macro? ns) (filter prefix (persistent! syms)))))

(defn- get-symbols-used-in-macros [file-content]
  (let [rdr (PushbackReader. (StringReader. (file-content-sans-ns file-content)))
        syms (transient [])
        ns (ns-from-string file-content)
        conj-symbol (fn [form] (when (symbol? form) (conj! syms form)))
        get-symbols-from-macro (fn [form]
                                 (if (and (sequential? form)
                                          (symbol? (first form))
                                          (macro? ns (first form)))
                                   (walk/postwalk conj-symbol form))
                                 form)]
    (loop [form (read rdr nil :eof)]
      (when (not= form :eof)
        (walk/prewalk get-symbols-from-macro form)
        (recur (read rdr nil :eof))))
    (map #(assoc {} :symbol % :suffix (suffix %) :prefix (prefix %))
         (set (persistent! syms)))))

(defn- remove-unused-requires [symbols-in-use libspecs]
  (map (partial remove-unused-syms-and-specs symbols-in-use) libspecs))

(defn- used-symbols-from-refer [libspecs file-content]
  (let [referred (set (remove nil? (mapcat get-referred-symbols libspecs)))
        referred-in-use (transient [])]
    (filter #(referred (symbol (:suffix %)))
            (get-symbols-used-in-macros file-content))))

(defn- adorn-with-name-and-alias [ns sym]
  (when-let [info (info-clj ns (:symbol sym))]
    (when (:candidates info)
      (throw (IllegalStateException. (str "Multiple candidates returned form symbol: " sym))))
    (try {:name (str (ns-name (:ns info)) "/" (:name info))
          :alias (when (prefix sym) sym)})))

(defn extract-dependencies [path ns-form]
  (let [libspecs (get-libspecs ns-form)
        file-content (slurp path)
        symbols-from-ast (-> file-content ns-ast used-vars set)
        symbols-from-refer (remove nil? (map (partial adorn-with-name-and-alias (second ns-form))
                                             (used-symbols-from-refer libspecs file-content)))
        fully-qualified-macros (map #(assoc {} :name %)
                                    (find-fully-qualified-macros file-content))]
    {:require (->> libspecs
                   (remove-unused-requires
                    (set (concat symbols-from-refer symbols-from-ast
                                 fully-qualified-macros)))
                   (filter (complement nil?)))
     :import (->> ns-form
                  get-imports
                  (remove-unused-imports symbols-from-ast))}))
