(ns refactor-nrepl.ns.prune-dependencies
  (:require
   [cider.nrepl.middleware.info :as info]
   [clojure.set :as set]
   [clojure.string :as string]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.find.symbols-in-file :as symbols-in-file]
   [refactor-nrepl.ns.libspec-allowlist :as libspec-allowlist]
   [refactor-nrepl.util :as util]))

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
  [{:keys [as as-alias ns refer refer-macros require-macros]} symbol-in-file]
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
   (and as (.startsWith ^String symbol-in-file (str as "/")))
   (and as-alias (.startsWith ^String symbol-in-file (str as-alias "/")))))

(defn- libspec-in-use-with-rename?
  [{:keys [rename]} symbols-in-file]
  (some (set symbols-in-file) (map str (vals rename))))

(defn- libspec-in-use?
  [{libspec-refer :refer
    libspec-ns :ns
    libspec-as :as
    :as libspec} symbols-in-file current-ns]
  (let [refer-all? (= libspec-refer :all)
        libspec-as-str (str libspec-as)
        symbols-in-file (cond-> symbols-in-file
                          (and (string? libspec-ns)
                               (not refer-all?)
                               libspec-as
                               (contains? (set symbols-in-file) libspec-as-str))
                          (conj (str (symbol libspec-ns libspec-as-str))))]
    (when (or (if refer-all?
                (some (partial libspec-in-use-with-refer-all? libspec current-ns)
                      symbols-in-file)
                (some (partial libspec-in-use-without-refer-all? libspec)
                      symbols-in-file))
              (libspec-in-use-with-rename? libspec symbols-in-file))
      libspec)))

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
(defn libspec-should-never-be-pruned?
  "Should `libspec` never be pruned away by the `clean-ns` op?"
  [libspec]
  (let [ns-name (str (:ns libspec))]
    (boolean (some (fn [^String pattern]
                     (-> pattern re-pattern (re-find ns-name)))
                   (libspec-allowlist/libspec-allowlist)))))

(defn imports->namespaces
  "Given a collection of `:import` clauses, returns the set of namespaces denoted by them, as symbols.

  Some of those namespace symbols may not refer to actual namespaces.
  e.g. a `java.io.File` import would return `java.io`, which isn't a Clojure namespace."
  [imports]
  (into #{}
        (map (fn [import]
               (-> (if (sequential? import)
                     (first import)
                     (->> (-> import str (string/split #"\."))
                          (butlast)
                          (string/join ".")))
                   str
                   (string/replace "_" "-")
                   symbol)))
        imports))

(defn libspec->namespaces
  "Given a libspec, returns the namespaces denoted by it (typically one, but possibly multiple,
  if prefix notation was used), as symbols."
  [libspec]
  (cond
    (symbol? libspec)
    [libspec]

    ;; Check if it doesn't denote prefix notation:
    (and (sequential? libspec)
         (or (-> libspec count #{1})
             (some keyword? libspec)))
    [(first libspec)]

    :else
    (let [suffixes (->> libspec
                        rest
                        (map (fn [suffix]
                               (cond-> suffix
                                 (sequential? suffix) first))))]
      (map (fn [prefix suffix]
             (symbol (str prefix "." suffix)))
           (repeat (first libspec))
           suffixes))))

(defn imports-contain-libspec?
  "Do `import-namespaces` contain at least one namespace that is denoted by `libspec`?

  This is useful for keeping requires that emit classes (i.e. those defining deftypes/defrecords),
  which are imported via `:import`."
  [imports-namespaces libspec]
  {:pre [(set? imports-namespaces)]}
  (let [require-namespaces (set (libspec->namespaces libspec))]
    (some? (seq (set/intersection imports-namespaces require-namespaces)))))

(defn- prune-libspec [symbols-in-file current-ns imports-namespaces libspec]
  (cond
    (libspec-should-never-be-pruned? libspec)
    libspec

    (imports-contain-libspec? imports-namespaces (:ns libspec))
    libspec

    :else
    (some->> libspec
             (remove-unused-renamed-symbols symbols-in-file)
             (remove-unused-requires symbols-in-file current-ns))))

(defn- prune-libspecs
  [libspecs symbols-in-file current-ns imports]
  (let [imports-namespaces (imports->namespaces imports)]
    (keep (partial prune-libspec symbols-in-file current-ns imports-namespaces)
          libspecs)))

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
                             set)
        ;; `imports` are calculated before `requires`, because
        ;; the former's needs affect whether the latter can be pruned:
        imports (prune-imports (some-> parsed-ns dialect :import)
                               symbols-in-file)
        requires (prune-libspecs required-libspecs symbols-in-file current-ns imports)]
    {dialect (merge {:require requires
                     :import imports}
                    (when (= dialect :cljs)
                      {:require-macros
                       (prune-libspecs required-macro-libspecs symbols-in-file current-ns #{})}))}))

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
