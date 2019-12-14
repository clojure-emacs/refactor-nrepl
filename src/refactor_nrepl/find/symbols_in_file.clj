(ns refactor-nrepl.find.symbols-in-file
  (:require [clojure
             [walk :as walk]]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as readers]
            [refactor-nrepl
             [core :as core]
             [util :as util]]
            [refactor-nrepl.ns.ns-parser :as ns-parser]
            [clojure.java.io :as io]))

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

  Note: Because it was convenient at the time this function also
  returns fully qualified keywords mapped to symbol.  This means that
  if the file contains `:prefix/kw` the symbol
  `fully.resolved.prefix/kw` is included in the set.

  Dialect defaults to :clj."
  ([path parsed-ns] (symbols-in-file path parsed-ns :clj))
  ([path parsed-ns dialect]
   (util/with-additional-ex-data [:file path]
     (let [dialect (or dialect (core/file->dialect path))
           file-ns (or (find-ns (symbol (:ns parsed-ns))) *ns*)
           ns-aliases (if (= dialect :cljs)
                        (ns-parser/aliases
                         (ns-parser/get-libspecs-from-file :cljs (io/file path)))
                        (ns-aliases file-ns))]
       (binding [*ns* file-ns
                 reader/*data-readers* (merge (when (= dialect :cljs)
                                                {'js identity})
                                              *data-readers*)
                 clojure.tools.reader/*alias-map* ns-aliases]
         (let [rdr (-> path slurp core/file-content-sans-ns
                       readers/indexing-push-back-reader)
               rdr-opts {:read-cond :allow :features #{dialect} :eof :eof}
               syms (atom #{})
               collect-symbol (fn [form]
                                ;; Regular symbol
                                (when (symbol? form)
                                  (swap! syms conj (normalize-ctor-call form)))
                                ;; Classes used in typehints
                                (when-let [t (:tag (meta form))]
                                  (swap! syms conj t))
                                (when (and (keyword? form)
                                           (core/fully-qualified? form))
                                  (swap! syms conj
                                         (symbol (core/prefix form)
                                                 (core/suffix form))))
                                form)]
           (loop [form (reader/read rdr-opts rdr)]
             (when (not= form :eof)
               (walk/prewalk collect-symbol form)
               (recur (reader/read rdr-opts rdr))))
           (->> @syms
                (map (partial fix-ns-of-backquoted-symbols (dialect parsed-ns)))
                set)))))))
