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
        syms (atom [])
        conj-symbol (fn [form] (when (symbol? form) (swap! syms conj form)))]
    (loop [form (read rdr nil :eof)]
      (when (not= form :eof)
        (walk/postwalk conj-symbol form)
        (recur (read rdr nil :eof))))
    (filter (partial macro? ns) (filter prefix @syms))))

(defn- get-symbols-used-in-macros
  "all symbols found below a macro form"
  [file-content]
  (let [rdr (PushbackReader. (StringReader. (file-content-sans-ns file-content)))
        syms (atom [])
        ns (ns-from-string file-content)
        conj-symbol (fn [form] (when (symbol? form) (swap! syms conj syms form)) form)
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
         (set @syms))))

(defn- remove-unused-requires [symbols-in-use libspecs]
  (map (partial remove-unused-syms-and-specs symbols-in-use) libspecs))

(defn- used-symbols-from-refer [libspecs symbols-used-in-macros]
  (let [referred (set (remove nil? (mapcat get-referred-symbols libspecs)))]
    (filter #(and (:suffix %) (referred (symbol (:suffix %)))) symbols-used-in-macros)))

(defn- filter-imports [imports symbols-used]
  (let [used (set symbols-used)]
    (map #(conj {} [:name %]) (filter #(used (suffix %)) imports))))

(defn- adorn-with-name-and-alias [ns sym]
  (when-let [info (info-clj ns (:symbol sym))]
    (when (:candidates info)
      (throw (IllegalStateException.
              (str "Multiple candidates returned form symbol: " sym))))
    {:name (str (ns-name (:ns info)) "/" (:name info))
     :alias (when (prefix sym) sym)}))

(defn- get-classes-used-in-typehints [file-content]
  (let [rdr (PushbackReader. (StringReader. (file-content-sans-ns file-content)))
        content (file-content-sans-ns file-content)
        types (atom [])
        conj-type (fn [form]
                    (when-let [t (:tag (meta form))] (swap! types conj (str t)))
                    form)]
    (loop [form (read rdr nil :eof)]
      (when (not= form :eof)
        (walk/prewalk conj-type form)
        (recur (read rdr nil :eof))))
    (set @types)))

(defn- get-symbols-to-prune-libspecs
  [ns-form libspecs file-content symbols-from-ast symbols-used-in-macros]
  (let [fully-qualified-macros (map #(assoc {} :name %)
                                    (find-fully-qualified-macros file-content))
        symbols-from-refer (used-symbols-from-refer libspecs symbols-used-in-macros)
        referred (remove nil? (map (partial adorn-with-name-and-alias
                                            (second ns-form))
                                   symbols-from-refer))]
    (set (concat referred symbols-from-ast fully-qualified-macros))))

(defn get-symbols-to-prune-imports
  [symbols-from-ast symbols-used-in-macros file-content ns-form]
  (let [imports (get-imports ns-form)
        imports-used-in-macros (filter-imports imports
                                               (map :suffix symbols-used-in-macros))
        imports-used-in-typehints (filter-imports imports
                                                  (get-classes-used-in-typehints file-content))]
    (concat symbols-from-ast imports-used-in-typehints imports-used-in-macros)))

(defn- prune-libspecs
  [libspecs file-content symbols-from-ast symbols-used-in-macros ns-form]
  (->> libspecs
       (remove-unused-requires
        (get-symbols-to-prune-libspecs ns-form libspecs
                                       file-content
                                       symbols-from-ast
                                       symbols-used-in-macros))
       (filter (complement nil?))))

(defn- prune-imports
  [ns-form symbols-from-ast symbols-used-in-macros file-content]
  (->> ns-form
       get-imports
       (remove-unused-imports
        (get-symbols-to-prune-imports symbols-from-ast
                                       symbols-used-in-macros
                                       file-content ns-form))))

(defn extract-dependencies [path ns-form]
  (let [libspecs (get-libspecs ns-form)
        file-content (slurp path)
        symbols-from-ast (-> file-content ns-ast used-vars set)
        symbols-used-in-macros (get-symbols-used-in-macros file-content)]
    {:require (prune-libspecs libspecs file-content symbols-from-ast
                              symbols-used-in-macros ns-form)
     :import (prune-imports ns-form symbols-from-ast
                            symbols-used-in-macros file-content)}))
