(ns refactor-nrepl.ns.dependencies
  (:require [clojure.walk :as walk]
            [refactor-nrepl.ns
             [helpers :refer [ctor-call->str file-content-sans-ns prefix suffix]]
             [ns-parser :refer [get-imports get-libspecs]]]
            [cider.nrepl.middleware.info :as info])
  (:import [java.io PushbackReader StringReader]))

(defn- lookup-symbol-ns
  [current-ns symbol-in-file]
  (when-let [ns (:ns (info/info {:ns current-ns :symbol symbol-in-file}))]
    (ns-name ns)))

(defn- libspec-in-use-with-refer-all?
  [{:keys [ns]} current-ns symbol-in-file]
  (= (lookup-symbol-ns current-ns symbol-in-file) ns))

(defn- libspec-in-use-without-refer-all?
  [{:keys [as ns refer] :as libspec}  current-ns symbol-in-file]
  (or
   ;; Used through refer clause
   (and (not= refer :all)
        ((into
          ;; fully qualified reference in file even though symbol is referred
          ;; This happens as a side-effect of using the symbol in a
          ;; backquoted form when writing macros
          (set (map (fn [symbol-from-refer]
                      (str (lookup-symbol-ns current-ns symbol-from-refer) "/"
                           symbol-from-refer))
                    refer))
          (map str refer)) symbol-in-file))
   ;; Used as a fully qualified symbol
   (.startsWith symbol-in-file (str ns "/"))
   ;; Aliased symbol in use
   (and as (.startsWith symbol-in-file (str as "/")))))

(defn- libspec-in-use?
  [{:keys [ns as refer] :as libspec} symbols-in-file current-ns]
  (when (if (= refer :all)
          (some (partial libspec-in-use-with-refer-all? libspec current-ns)
                symbols-in-file)
          (some (partial libspec-in-use-without-refer-all? libspec current-ns)
                symbols-in-file))
    libspec))

(defn- referred-symbol-in-use?
  [current-ns used-syms sym]
  (some (fn [sym-from-file]
          ((into #{(str sym)} [(str (lookup-symbol-ns current-ns sym) "/" sym)])
           sym-from-file))
        used-syms))

(defn- remove-unused-referred-symbols
  [refer-clause current-ns used-syms]
  (if (= refer-clause :all)
    refer-clause
    (filter (partial referred-symbol-in-use? current-ns used-syms) refer-clause)))

(defn- prune-refer
  [libspec used-syms current-ns]
  (update-in libspec [:refer]
             remove-unused-referred-symbols current-ns used-syms))

(defn remove-empty-refer
  [{:keys [refer] :as libspec}]
  (if (and (not= refer :all) (empty? refer))
    (dissoc libspec :refer)
    libspec))

(defn- remove-unused-syms-and-specs
  [used-syms current-ns libspec]
  (some-> libspec
          (libspec-in-use? used-syms current-ns)
          (prune-refer used-syms current-ns)
          remove-empty-refer))

(defn- static-method-or-field-access->Classname
  [symbol-in-file]
  (when (re-find #"/" (str symbol-in-file))
    (-> symbol-in-file
        str
        (.split "/")
        first
        suffix)))

(defn- class-in-use?
  [symbols-in-file c]
  (or
   ;; fully.qualified.Class
   (symbols-in-file c)
   ;; OnlyClassName or Class$Enum/Value
   ((set (map suffix symbols-in-file)) (suffix c))
   ;; Static/fieldOrMethod
   ((set (map static-method-or-field-access->Classname symbols-in-file))
    (suffix c))))

(defn- remove-unused-imports
  [imports symbols-in-file]
  (filter (partial class-in-use? symbols-in-file) imports))

(defn- get-referred-symbols
  [libspec]
  (when-not (or (symbol? libspec)
                (= (:refer libspec) :all))
    (:refer libspec)))

(defn- fix-ns-of-backquoted-forms
  [current-ns sym]
  ;; When the reader reads backquoted forms the symbol
  ;; is fully qualified using the value of *ns* at read
  ;; time
  (if (.contains sym (str (ns-name *ns*)))
    (str (lookup-symbol-ns current-ns (suffix sym)) "/" (suffix sym))
    sym))

(defn- get-symbols-used-in-file
  [file-content current-ns]
  (let [rdr (PushbackReader. (StringReader. (file-content-sans-ns file-content)))
        syms (atom [])
        collect-symbol (fn [form]
                         (when (symbol? form)
                           (swap! syms conj (ctor-call->str form)))
                         form)]
    (loop [form (read rdr nil :eof)]
      (when (not= form :eof)
        (walk/prewalk collect-symbol form)
        (recur (read rdr nil :eof))))
    (set (map (partial fix-ns-of-backquoted-forms current-ns) @syms))))

(defn- remove-unused-requires [symbols-in-file current-ns libspecs]
  (map (partial remove-unused-syms-and-specs symbols-in-file current-ns) libspecs))

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

(defn- prune-libspecs
  [libspecs symbols-in-file current-ns]
  (->> libspecs
       (remove-unused-requires symbols-in-file current-ns)
       (filter (complement nil?))))

(defn- prune-imports
  [ns-form symbols-in-file]
  (-> ns-form
      get-imports
      (remove-unused-imports symbols-in-file)))

(defn extract-dependencies [path ns-form]
  (let [libspecs (get-libspecs ns-form)
        file-content (slurp path)
        current-ns (second ns-form)
        symbols-in-file (into (get-symbols-used-in-file file-content current-ns)
                              (get-classes-used-in-typehints file-content))]
    {:require (prune-libspecs libspecs symbols-in-file current-ns)
     :import (prune-imports ns-form symbols-in-file)}))
