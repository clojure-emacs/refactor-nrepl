(ns refactor-nrepl.ns.dependencies
  (:require [cider.nrepl.middleware.info :as info]
            [clojure.walk :as walk]
            [refactor-nrepl.ns
             [helpers :refer [ctor-call->str file-content-sans-ns prefix suffix]]
             [ns-parser :refer [get-imports get-libspecs]]]
            [refactor-nrepl.util :as util])
  (:import [java.io PushbackReader StringReader]))

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

(defn- prune-key [libspec key used-syms]
  (let [val (key libspec)]
    (if (and val (not (keyword val)))
      (assoc libspec key
             (filter (partial referred-symbol-in-use? (:ns libspec) used-syms)
                     (key libspec)))
      libspec)))

(defn- remove-unused-syms-and-specs
  [used-syms current-ns libspec]
  (some-> libspec
          (libspec-in-use? used-syms current-ns)
          (prune-key :refer used-syms)
          (prune-key :refer-macros used-syms)
          (prune-key :require-macros used-syms)
          (util/dissoc-when (fn empty-and-not-kw [k]
                              (and (not (keyword k)) (empty? k)))
                            :refer :refer-macros :require-macros)))

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

(defn- find-symbol-ns [libspecs sym]
  (some->> libspecs
           (filter (fn [{:keys [refer refer-macros] :as libspec}]
                     (or (some (partial = (symbol sym)) refer)
                         (some (partial = (symbol sym)) refer-macros))))
           first
           :ns))

(defn- fix-ns-of-backquoted-symbols [libspecs sym]
  (if (= (prefix sym) (str (ns-name *ns*)))
    (if-let [prefix (find-symbol-ns libspecs (suffix sym))]
      (str prefix "/" (suffix sym))
      sym)
    sym))

(defn- get-symbols-used-in-file
  [file-content current-ns libspecs]
  (binding [*ns* (or (find-ns (symbol current-ns)) *ns*)]
    (let [rdr (PushbackReader. (StringReader. (file-content-sans-ns file-content)))
          syms (atom #{})
          collect-symbol (fn [form]
                           (when (symbol? form)
                             (swap! syms conj (ctor-call->str form)))
                           form)]
      (loop [form (read rdr nil :eof)]
        (when (not= form :eof)
          (walk/prewalk collect-symbol form)
          (recur (read rdr nil :eof))))
      (set (map (partial fix-ns-of-backquoted-symbols libspecs) @syms)))))

(defn- remove-unused-requires [symbols-in-file current-ns libspecs]
  (map (partial remove-unused-syms-and-specs symbols-in-file current-ns)
       libspecs))

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

(defn extract-dependencies [ns-form path]
  (let [libspecs (get-libspecs ns-form)
        file-content (slurp path)
        current-ns (second ns-form)
        symbols-in-file (into (get-symbols-used-in-file file-content current-ns
                                                        libspecs)
                              (get-classes-used-in-typehints file-content))]
    {:require (prune-libspecs libspecs symbols-in-file current-ns)
     :import (prune-imports ns-form symbols-in-file)}))
