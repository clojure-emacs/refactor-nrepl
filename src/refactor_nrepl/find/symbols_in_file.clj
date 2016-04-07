(ns refactor-nrepl.find.symbols-in-file
  (:require [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as readers]
            [clojure.walk :as walk]
            [refactor-nrepl
             [core :as core]
             [util :as util]]))

(defn- find-symbol-ns [{:keys [:require :require-macros] :as dependencies} sym]
  (some->> (into require require-macros)
           (filter (fn [{:keys [refer refer-macros] :as libspec}]
                     (or
                      (and (sequential? refer)
                           (some (partial = (symbol sym)) refer))
                      (some (partial = (symbol sym)) refer-macros))))
           first
           :ns))

(defn- fix-ns-of-backquoted-symbols [dependencies sym]
  (if (= (core/prefix sym) (str (ns-name *ns*)))
    (if-let [prefix (find-symbol-ns dependencies (core/suffix sym))]
      (str prefix "/" (core/suffix sym))
      sym)
    sym))

(defn- normalize-ctor-call
  "Date. -> \"Date\""
  [sym]
  (let [s (str sym)]
    (symbol (if (.endsWith s ".")
              (.substring s 0 (dec (.length s)))
              s))))

(defn symbols-in-file
  "Return a set of all the symbols occurring in the file at path.

  This includes all regular symbols like foo, but also ctor calls like
  Foo. (returned as Foo), and classes used in typehints.

  Dialect defaults to :clj."
  ([path parsed-ns] (symbols-in-file path parsed-ns :clj))
  ([path parsed-ns dialect]
   (util/with-additional-ex-data [:file path]
     (binding [*ns* (or (find-ns (symbol (:ns parsed-ns))) *ns*)
               reader/*data-readers* *data-readers*]
       (let [rdr (-> path slurp core/file-content-sans-ns
                     readers/indexing-push-back-reader)
             dialect (or dialect (core/file->dialect path))
             rdr-opts {:read-cond :allow :features #{dialect} :eof :eof}
             syms (atom #{})
             collect-symbol (fn [form]
                              ;; Regular symbol
                              (when (symbol? form)
                                (swap! syms conj (normalize-ctor-call form)))
                              ;; Classes used in typehints
                              (when-let [t (:tag (meta form))]
                                (swap! syms conj t))
                              form)]
         (loop [form (reader/read rdr-opts rdr)]
           (when (not= form :eof)
             (walk/prewalk collect-symbol form)
             (recur (reader/read rdr-opts rdr))))
         (->> @syms
              (map (partial fix-ns-of-backquoted-symbols (dialect parsed-ns)))
              set))))))
