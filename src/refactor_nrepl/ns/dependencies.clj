(ns refactor-nrepl.ns.dependencies
  (:require [cider.nrepl.middleware.info :as info]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as readers]
            [clojure.walk :as walk]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.ns [ns-parser :as ns-parser]]
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
  [{:keys [as ns refer refer-macros require-macros] :as libspec} symbol-in-file]
  (or
   ;; Used through refer clause
   (and (not= refer :all)
        ((into
          ;; fully qualified reference in file even though symbol is referred
          ;; This happens as a side-effect of using the symbol in a
          ;; backquoted form when writing macros
          (set (map (fn [symbol-from-refer]
                      (str ns "/" symbol-from-refer))
                    (concat refer require-macros refer-macros)))
          (map str (concat refer require-macros refer-macros))) symbol-in-file))
   ;; Used as a fully qualified symbol
   (.startsWith symbol-in-file (str ns "/"))
   ;; Aliased symbol in use
   (and as (.startsWith symbol-in-file (str as "/")))))

(defn- libspec-in-use?
  [{:keys [ns as refer] :as libspec} symbols-in-file current-ns]
  (when (if (= refer :all)
          (some (partial libspec-in-use-with-refer-all? libspec current-ns)
                symbols-in-file)
          (some (partial libspec-in-use-without-refer-all? libspec)
                symbols-in-file))
    libspec))

(defn- referred-symbol-in-use?
  [symbol-ns used-syms sym]
  (some (fn [sym-from-file]
          ((into #{(str sym)} [(str symbol-ns "/" sym)])
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
   ;; OnlyClassName or Class$Enum/Value
   ((set (map core/suffix symbols-in-file)) (core/suffix c))
   ;; Static/fieldOrMethod
   ((set (map static-method-or-field-access->Classname symbols-in-file))
    (core/suffix c))))

(defn- get-referred-symbols
  [libspec]
  (when-not (or (symbol? libspec)
                (= (:refer libspec) :all))
    (:refer libspec)))

(defn- find-symbol-ns [libspecs sym]
  (some->> libspecs
           (filter (fn [{:keys [refer refer-macros] :as libspec}]
                     (or
                      (and (sequential? refer)
                           (some (partial = (symbol sym)) refer))
                      (some (partial = (symbol sym)) refer-macros))))
           first
           :ns))

(defn- fix-ns-of-backquoted-symbols [libspecs sym]
  (if (= (core/prefix sym) (str (ns-name *ns*)))
    (if-let [prefix (find-symbol-ns libspecs (core/suffix sym))]
      (str prefix "/" (core/suffix sym))
      sym)
    sym))

(defn- ctor-call->str
  "Date. -> \"Date\""
  [sym]
  (let [s (str sym)]
    (if (.endsWith s ".")
      (.substring s 0 (dec (.length s)))
      s)))

(defn- get-symbols-used-in-file
  [path current-ns libspecs dialect]
  (util/with-additional-ex-data [:file path]
    (binding [*ns* (or (find-ns (symbol current-ns)) *ns*)]
      (let [rdr (-> path slurp core/file-content-sans-ns
                    readers/indexing-push-back-reader)
            dialect (or dialect (core/file->dialect path))
            rdr-opts {:read-cond :allow :features #{dialect} :eof :eof}
            syms (atom #{})
            collect-symbol (fn [form]
                             (when (symbol? form)
                               (swap! syms conj (ctor-call->str form)))
                             form)]
        (loop [form (reader/read rdr-opts rdr)]
          (when (not= form :eof)
            (walk/prewalk collect-symbol form)
            (recur (reader/read rdr-opts rdr))))
        (set (map (partial fix-ns-of-backquoted-symbols libspecs) @syms))))))

(defn- remove-unused-requires [symbols-in-file current-ns libspecs]
  (map (partial remove-unused-syms-and-specs symbols-in-file current-ns)
       libspecs))

(defn- get-classes-used-in-typehints [path]
  (util/with-additional-ex-data [:file path]
    (let [rdr (-> path slurp core/file-content-sans-ns readers/indexing-push-back-reader)
          rdr-opts {:read-cond :allow :features #{:clj} :eof :eof}
          types (atom [])
          conj-type (fn [form]
                      (when-let [t (:tag (meta form))] (swap! types conj (str t)))
                      form)]
      (loop [form (reader/read rdr-opts rdr)]
        (when (not= form :eof)
          (walk/prewalk conj-type form)
          (recur (reader/read rdr-opts rdr))))
      (set @types))))

(defn- prune-libspecs
  [libspecs symbols-in-file current-ns]
  (->> libspecs
       (remove-unused-requires symbols-in-file current-ns)
       (filter (complement nil?))))

(defn- prune-imports
  [imports symbols-in-file]
  (filter (partial class-in-use? symbols-in-file) imports))

(defn extract-clj-or-cljs-dependencies
  ([path]
   (extract-clj-or-cljs-dependencies path nil))
  ([path dialect]
   (let [parsed-ns (ns-parser/parse-ns path)
         dialect (or dialect (core/file->dialect path))
         {current-ns :ns} parsed-ns
         required-libspecs (some-> parsed-ns dialect :require)
         required-macro-libspecs (some-> parsed-ns :cljs :require-macros)
         symbols-in-file (into (get-classes-used-in-typehints path)
                               (get-symbols-used-in-file path current-ns
                                                         (into required-libspecs
                                                               required-macro-libspecs)
                                                         dialect))]
     {dialect (merge {:require
                      (prune-libspecs required-libspecs  symbols-in-file current-ns)
                      :import (prune-imports (some-> parsed-ns dialect :import)
                                             symbols-in-file)}
                     (when (= dialect :cljs)
                       {:require-macros
                        (prune-libspecs required-macro-libspecs symbols-in-file
                                        current-ns)}))})))

(defn- extract-cljc-dependencies [path]
  (merge
   (extract-clj-or-cljs-dependencies path :clj)
   (extract-clj-or-cljs-dependencies path :cljs)))

(defn extract-dependencies [path]
  (let [dialect (core/file->dialect path)]
    (merge (if (= dialect :cljc)
             (extract-cljc-dependencies path)
             (extract-clj-or-cljs-dependencies path))
           {:source-dialect dialect})))
