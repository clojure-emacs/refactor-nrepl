(ns refactor-nrepl.ns.dependencies
  (:require [cider.nrepl.middleware.info :refer [info-clj]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [instaparse.core :refer [parse parser]]
            [refactor-nrepl.analyzer :refer [string-ast]]
            [refactor-nrepl.ns.helpers :refer [get-ns-component]])
  (:import java.util.regex.Pattern))

(defn parse-form
  "Form is either (:import..) (:use ..) or (:require ..)"
  [form]
  (let [ns-parser (parser
                   (io/resource "refactor_nrepl/ns/require-or-use-or-import.bnf")
                   :auto-whitespace :comma)]
    (parse ns-parser (str form))))

(defn- add-prefix-to-libspec
  [prefix libspec]
  (if (sequential? libspec)
    (let [suffix (second libspec)]
      (assoc libspec 0 :libspec-with-opts
             1 (str prefix "." suffix)))
    (str prefix "." libspec)))

(defn- use-to-refer-all
  [libspec]
  (if (nil? (:refer libspec))
    (update-in libspec [:refer] (constantly :all))
    libspec))

(defmulti parse-libspec first)

(defn- extract-referred [libspec]
  (let [refer (some->> libspec (drop-while #(not= % ":refer")) second)]
    (if (sequential? refer)
      (map symbol (rest refer))
      (when refer
        :all))))

(defn- extract-rename-spec [libspec]
  (some->> libspec
           (drop-while #(not= % ":rename"))
           second
           rest
           (map symbol)
           (apply hash-map)))

(defmethod parse-libspec :libspec-with-opts
  [[_ ns & libspec]]
  {:ns (symbol ns)
   :as (some->> libspec (drop-while #(not= % ":as")) second symbol)
   :refer (extract-referred libspec)
   :rename (extract-rename-spec libspec)
   :only (some->> libspec (drop-while #(not= % ":only")) second rest)
   :flags (some->> libspec (filter #{":reload" ":reload-all" ":verbose"}))})

(defmethod parse-libspec :libspec-no-opts
  [[_ ns]]
  {:ns (symbol ns)})

(defmethod parse-libspec :prefix-libspec
  [[_ prefix & libspecs]]
  (->> libspecs
       (map (partial add-prefix-to-libspec prefix))
       (map parse-libspec)))

(defn- extract-libspecs
  [form]
  (flatten
   (let [parse-tree (rest (second (parse-form form)))]
     (for [libspec parse-tree]
       (parse-libspec libspec)))))

(defmulti parse-import first)

(defmethod parse-import :class
  [import]
  (second import))

(defn- add-package-prefix-to-class
  [prefix [_ class-name :as class]]
  (assoc class 1 (str prefix "." class-name)))

(defmethod parse-import :classes-with-prefix
  [[_ prefix & classes]]
  (->> classes
       (map (partial add-package-prefix-to-class prefix))
       (map parse-import)))

(defn extract-imports
  [form]
  (let [parse-tree (rest (second (parse-form form)))]
    (for [import parse-tree]
      (parse-import import))))

(defn extract-used [use-form]
  (some->>  use-form
            extract-libspecs
            (map use-to-refer-all)))

(defn- extract-requires [require-form]
  (some-> require-form
          extract-libspecs))

(defn get-libspecs [ns-form]
  (concat
   (extract-used (get-ns-component ns-form :use))
   (extract-requires (get-ns-component ns-form :require))))

(defn- get-imports [ns-form]
  (some-> (get-ns-component ns-form :import) extract-imports
          flatten))

(defn- class-op?
  [op]
  (or (= op :static-call)
      (= op :const)
      (= op :static-field)
      (= op :new)))

(defn- get-class-name [{:keys [op class type val children] :as node}]
  (when-not children
    (try
      (cond
        (and (class-op? op) (:type :class)) (.getName val)
        (and class (not= op :import)) (str (.getName class)))
      (catch Exception e
        (println e)))))

(defn- get-var-name [node]
  (some-> node
          :var
          str
          (str/replace "#'" "")))

(defn- node->var [node]
  (or (get-class-name node)
      (get-var-name node)))

(defn- used-vars
  "Finds used functions and classes"
  [ast]
  (->> ast
       (map nodes)
       flatten
       (map node->var)
       (remove str/blank?)))

(defn- ns-in-use?
  [ns used-syms]
  (some #(.startsWith % (str ns "/")) used-syms))

(defn- prune-refer
  [refer-clause prefix used-syms]
  (map #(-> (str/split % #"/") second symbol)
       (filter (set used-syms)
               (map #(str prefix "/" %) refer-clause))))

(defn- remove-unused
  [used-syms {:keys [ns refer as] :as libspec}]
  (when (ns-in-use? ns used-syms)
    (if (= refer :all)
      libspec
      (update-in libspec [:refer] prune-refer ns used-syms))))

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
  (filter (set symbols-in-use) imports))

(defn- macro? [ns sym]
  (:macro (info-clj ns sym)))

(defn- add-prefix
  [prefix var]
  (symbol (str prefix "/" var)))

(defn- get-referred-macros-from-refer
  [libspec]
  (when-not (or (symbol? libspec)
                (= (:refer libspec) :all))
    (filter (partial macro? (:ns libspec)) (:refer libspec))))

(defn- find-fully-qualified-macros [file-content]
  (->> file-content
       (re-matches #"[A-Za-z0-9_.?$%!-]+/[A-Za-z0-9_?$%!*-]+")
       (map symbol)
       (filter macro?)))

(defn- macro-in-use? [file-content macro]
  (when (-> macro
            (str "(\\b|\\B)")
            re-pattern
            (re-find file-content)
            empty?
            not)
    macro))

(defn- get-referred-macros [file-content libspecs]
  (remove nil? (flatten
                (for [libspec libspecs]
                  (some->> libspec
                           get-referred-macros-from-refer
                           (map (partial macro-in-use? file-content))
                           (map (partial add-prefix (:ns libspec))))))))

(defn- used-macros [file-content libspecs]
  (let [referred-macros-in-use  (get-referred-macros file-content libspecs)]
    (->> file-content
         find-fully-qualified-macros
         (filter (partial macro-in-use? file-content))
         (concat referred-macros-in-use)
         (map str))))

(defn- remove-unused-requires [symbols-in-use libspecs]
  (map (partial remove-unused-syms-and-specs symbols-in-use) libspecs))

(defn extract-dependencies [path ns-form]
  (let [libspecs (get-libspecs ns-form)
        file-content (slurp path)
        symbols-in-use (-> file-content string-ast used-vars)
        macros-in-use (used-macros file-content libspecs)]
    {:require (->> libspecs
                   (remove-unused-requires (concat macros-in-use symbols-in-use))
                   (filter (complement nil?)))
     :import (->> ns-form
                  get-imports
                  (remove-unused-imports symbols-in-use))}))
