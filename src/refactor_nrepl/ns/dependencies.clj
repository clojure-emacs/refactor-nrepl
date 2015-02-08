(ns refactor-nrepl.ns.dependencies
  (:require [cider.nrepl.middleware.info :refer [info-clj]]
            [clojure.string :as str]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [refactor-nrepl.analyzer :refer [ns-ast]]
            [refactor-nrepl.ns
             [helpers :refer [suffix]]
             [parser :refer [get-imports get-libspecs]]])
  (:import java.util.regex.Pattern))

(defn- get-class-name [{:keys [op class type val] :as node}]
  (when-let [c (if (and (= :const op)
                        (= :class type))
                 val
                 ;; catches static-invoke/static-field
                 (when (class? class)
                   class))]
    (.getName ^Class c)))

(defn- get-var-name [node]
  (some-> node
          :var
          str
          (str/replace "#'" "")))

(defn- get-interface-name [{:keys [op interface]}]
  (when (and interface
             (= op :method))
    (.getName ^Class interface)))

(defn- node->var [node]
  (or (get-class-name node)
      (get-var-name node)
      (get-interface-name node)))

(defn- used-vars
  "Finds used functions and classes"
  [ast]
  (->> ast
       (map nodes)
       flatten
       (map node->var)
       (remove str/blank?)
       set))

(defn- ns-in-use?
  [ns used-syms]
  (some #(.startsWith % (str ns)) used-syms))

(defn- prune-refer
  [refer-clause prefix used-syms]
  (filter (set (map #(-> % suffix symbol) used-syms))
          refer-clause))

(defn- remove-unused
  [used-syms {:keys [ns refer as] :as libspec}]
  (when (ns-in-use? ns used-syms)
    (if (= refer :all)
      libspec
      (update-in libspec [:refer] prune-refer ns used-syms))))

(defn- remove-empty-refer
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
  (let [m (java.util.regex.Pattern/quote (str macro))
        before "(\\(?|,|`|'|\\s)\\s*"
        after "\\s*\\)?"]
    (when (-> (str before m after)
              re-pattern
              (re-find file-content)
              empty?
              not)
      macro)))

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
        symbols-in-use (-> file-content ns-ast used-vars)
        macros-in-use (used-macros file-content libspecs)]
    {:require (->> libspecs
                   (remove-unused-requires (concat macros-in-use symbols-in-use))
                   (filter (complement nil?)))
     :import (->> ns-form
                  get-imports
                  (remove-unused-imports symbols-in-use))}))
