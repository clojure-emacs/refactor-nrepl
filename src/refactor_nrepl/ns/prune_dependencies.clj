(ns refactor-nrepl.ns.prune-dependencies
  (:require [cider.nrepl.middleware.info :as info]
            [refactor-nrepl
             [core :as core]
             [util :as util]]
            [refactor-nrepl.find.symbols-in-file :as symbols-in-file]
            [refactor-nrepl.config :as config]
            [refactor-nrepl.s-expressions :as sexp]))

(defn- lookup-symbol-ns
  ([ns symbol]
   (lookup-symbol-ns ns symbol {}))
  ([current-ns symbol-in-file session]
   (when-let [ns (:ns (info/info {:ns current-ns :symbol symbol-in-file
                                  :session session}))]
     (ns-name ns))))

(defn- libspec-in-use-with-refer-all?
  [{:keys [ns]} current-ns symbol-in-file]
  (= (lookup-symbol-ns current-ns symbol-in-file) ns))

(defn- libspec-in-use-without-refer-all?
  [{:keys [as ns refer refer-macros require-macros] :as libspec} symbol-in-file]
  (or
   ;; Used through refer clause
   (and (not= refer :all)
        ((into
          ;; fully qualified reference in file even though symbol is referred
          ;; This happens as a side-effect of using the symbol in a
          ;; backquoted form when writing macros
          (set (map (partial core/fully-qualify ns)
                    (concat refer require-macros refer-macros)))
          (map str (concat refer require-macros refer-macros))) symbol-in-file))
   ;; Used as a fully qualified symbol
   (.startsWith ^String symbol-in-file (str ns "/"))
   ;; Aliased symbol in use
   (and as (.startsWith ^String symbol-in-file (str as "/")))))

(defn- libspec-in-use-with-rename?
  [{:keys [rename] :as libspec} symbols-in-file]
  (some (set symbols-in-file) (map str (vals rename))))

(defn- libspec-in-use?
  [{:keys [ns as refer] :as libspec} symbols-in-file current-ns]
  (when (or (if (= refer :all)
              (some (partial libspec-in-use-with-refer-all? libspec current-ns)
                    symbols-in-file)
              (some (partial libspec-in-use-without-refer-all? libspec)
                    symbols-in-file))
            (libspec-in-use-with-rename? libspec symbols-in-file))
    libspec))

(defn- referred-symbol-in-use?
  [symbol-ns used-syms sym]
  (some (fn [sym-from-file]
          ((into #{(str sym)} [(core/fully-qualify symbol-ns sym)])
           sym-from-file))
        used-syms))

(defn- prune-key [libspec k used-syms]
  (let [val (k libspec)]
    (if (and val (not (keyword val)))
      (assoc libspec k
             (filter (partial referred-symbol-in-use? (:ns libspec) used-syms)
                     (k libspec)))
      libspec)))

(defn- remove-unused-syms-and-specs
  [used-syms current-ns libspec]
  (some-> libspec
          (libspec-in-use? used-syms current-ns)
          (prune-key :refer used-syms)
          (prune-key :refer-macros used-syms)
          (util/dissoc-when (fn empty-and-not-kw [k]
                              (and (not (keyword k)) (empty? k)))
                            :refer :refer-macros)))

(defn- static-method-or-field-access->Classname
  [symbol-in-file]
  (when (re-find #"/" (str symbol-in-file))
    (-> symbol-in-file
        str
        (.split "/")
        first
        core/suffix)))

(defn- class-in-use?
  [symbols-in-file c]
  (or
   ;; fully.qualified.Class
   (symbols-in-file c)
   ;; OnlyClassName, Class$Enum/Value or Class$Innerclass$InnerInnerClass
   ((set (map core/suffix symbols-in-file)) (core/suffix c))
   ;; Static/fieldOrMethod
   ((set (map static-method-or-field-access->Classname symbols-in-file))
    (core/suffix c))))

(defn- get-referred-symbols
  [libspec]
  (when-not (or (symbol? libspec)
                (= (:refer libspec) :all))
    (:refer libspec)))

(defn- remove-unused-requires [symbols-in-file current-ns libspec]
  (remove-unused-syms-and-specs symbols-in-file current-ns libspec))

(defn- remove-unused-renamed-symbols
  [symbols-in-file {:keys [rename] :as libspec}]
  (assoc libspec :rename
         (into {}
               (for [[sym alias] rename
                     :when (symbols-in-file (str alias))]
                 [sym alias]))))

;; Some namespaces, e.g. those containing only protocol extensions,
;; are side-effecting at load but might look unused and otherwise be
;; pruned.
(defn- libspec-should-never-be-pruned? [libspec]
  (let [ns-name (str (:ns libspec))]
    (some (fn [^String pattern]
            (re-find (re-pattern pattern) ns-name))
          (:libspec-whitelist config/*config*))))

(defn- prune-libspec [symbols-in-file current-ns libspec]
  (if (libspec-should-never-be-pruned? libspec)
    libspec
    (some->> libspec
             (remove-unused-renamed-symbols symbols-in-file)
             (remove-unused-requires symbols-in-file current-ns))))

(defn- prune-libspecs
  [libspecs symbols-in-file current-ns]
  (->> libspecs
       (map (partial prune-libspec symbols-in-file current-ns))
       (filter (complement nil?))))

(defn- prune-imports
  [imports symbols-in-file]
  (filter (partial class-in-use? symbols-in-file) imports))

(defn- prune-clj-or-cljs-dependencies
  [parsed-ns path dialect]
  (let [current-ns (:ns parsed-ns)
        required-libspecs (some-> parsed-ns dialect :require)
        required-macro-libspecs (some-> parsed-ns :cljs :require-macros)
        symbols-in-file (->> (symbols-in-file/symbols-in-file path parsed-ns
                                                              dialect)
                             (map str)
                             set)]
    {dialect (merge {:require
                     (prune-libspecs required-libspecs symbols-in-file current-ns)
                     :import (prune-imports (some-> parsed-ns dialect :import)
                                            symbols-in-file)}
                    (when (= dialect :cljs)
                      {:require-macros
                       (prune-libspecs required-macro-libspecs symbols-in-file
                                       current-ns)}))}))

(defn- prune-cljc-dependencies [parsed-ns path]
  (merge
   (prune-clj-or-cljs-dependencies parsed-ns path :clj)
   (prune-clj-or-cljs-dependencies parsed-ns path :cljs)))

(defn prune-dependencies [parsed-ns path]
  (let [dialect (core/file->dialect path)]
    (merge (if (= dialect :cljc)
             (prune-cljc-dependencies parsed-ns path)
             (prune-clj-or-cljs-dependencies parsed-ns path dialect))
           {:source-dialect dialect})))
