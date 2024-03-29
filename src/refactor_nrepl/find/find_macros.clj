(ns refactor-nrepl.find.find-macros
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.reader :as reader]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.find.bindings :as bindings]
   [refactor-nrepl.ns.ns-parser :as ns-parser]
   [refactor-nrepl.ns.tracker :as tracker]
   [refactor-nrepl.s-expressions :as sexp]
   [refactor-nrepl.util :as util]
   [rewrite-clj.zip :as zip])
  (:import
   (clojure.lang LineNumberingPushbackReader)
   (java.io File FileReader StringReader)))

;; The structure here is {path [timestamp macros]}
(def ^:private macro-defs-cache (atom {}))

(defn- keep-lines
  "Keep the first n lines of s."
  [s n]
  (->> s
       StringReader.
       java.io.BufferedReader.
       line-seq
       (take n)
       doall
       (str/join "\n")))

(defn- build-macro-meta
  [form ^File f]
  (let [{:keys [line column end-line end-column]} (meta form)
        file-content (slurp f)
        file-ns (core/ns-from-string file-content)
        content (keep-lines file-content end-line)
        sexp (sexp/get-last-sexp content)
        macro-name (name (second form))]
    {:name (str file-ns "/" macro-name)
     :col-beg column
     :col-end end-column
     :line-beg line
     :line-end end-line
     :file (.getAbsolutePath f)
     :match sexp}))

(defn- find-macro-definitions-in-file
  [^File f]
  (util/with-additional-ex-data [:file (.getAbsolutePath f)]
    (with-open [file-rdr (FileReader. f)]
      (binding [*ns* (or (core/path->namespace :no-error f) *ns*)
                reader/*read-eval* false
                reader/*data-readers* *data-readers*]
        (let [rdr (LineNumberingPushbackReader. file-rdr)
              opts {:read-cond :allow :features #{:clj} :eof :eof}]
          (loop [macros [], form (reader/read opts rdr)]
            (cond
              (or (= form :eof)
                  (util/interrupted?)) macros
              (and (sequential? form) (= (first form) 'defmacro))
              (recur (conj macros (build-macro-meta form f))
                     (reader/read opts rdr))
              :else
              (recur macros (reader/read opts rdr)))))))))

(defn- get-cached-macro-definitions [^File f]
  (when-let [[ts v] (get @macro-defs-cache (.getAbsolutePath f))]
    (when (= ts (.lastModified f))
      v)))

(defn- put-cached-macro-definitions [^File f]
  (let [defs (find-macro-definitions-in-file f)
        ts (.lastModified f)]
    (swap! macro-defs-cache assoc-in [(.getAbsolutePath f)] [ts defs])
    defs))

(defn- get-macro-definitions-in-file-with-caching [f]
  (if-let [v (get-cached-macro-definitions f)]
    v
    (put-cached-macro-definitions f)))

(defn- find-macro-definitions-in-project
  "Finds all macros that are defined in the project."
  [ignore-errors?]
  (->> (core/find-in-project (util/with-suppressed-errors
                               (every-pred (complement util/interrupted?)
                                           (some-fn core/cljc-file? core/clj-file?))
                               ignore-errors?)
                             (core/source-dirs-on-classpath))
       (mapcat #(try
                  (get-macro-definitions-in-file-with-caching %)
                  (catch Exception e
                    (util/maybe-log-exception e)
                    (when-not ignore-errors?
                      (throw e)))))))

(defn- get-ns-aliases
  "Create a map of ns-aliases to namespaces."
  [libspecs]
  (->>  libspecs
        (map #((juxt (comp str :ns) (comp str :as)) %))
        (into {})))

(defn- referred-from-require?
  [libspec macro-name]
  (some->> (when (sequential? (:refer libspec)) (:refer libspec))
           (map str)
           (some #(= % (core/suffix macro-name)))))

(defn- referred-from-use?
  [libspec macro-name]
  (some->> libspec
           :use
           (map str)
           (filter #(= % (core/suffix macro-name)))
           first))

(defn- macro-referred? [libspecs macro-name]
  (let [libspec (some->> libspecs
                         (filter #(= (str (:ns %)) (core/prefix macro-name)))
                         first)]
    (or (referred-from-require? libspec macro-name)
        (referred-from-use? libspec macro-name))))

(defn- node->occurrence
  "line-offset is the offset in the file where we start searching.
  This is after the ns but clj-rewrite keeps tracking of any following
  whitespace."
  [path macro-name ^long line-offset zip-node]
  (let [node (zip/node zip-node)
        ^String val (:string-value node)
        {:keys [^long row ^long col]} (meta node)]
    {:match (zip/string (zip/up zip-node))
     :name macro-name
     :line-beg (dec (+ row line-offset))
     :line-end (dec (+ row line-offset))
     :col-beg col
     :col-end (+ col (.length val))
     :file path}))

(defn- token-node? [node]
  (= (zip/tag node) :token))

(defn- macro-found?
  [sym libspecs current-ns macro-name]
  (let [macro-prefix (core/prefix macro-name)
        macro-suffix (core/suffix macro-name)
        alias? ((get-ns-aliases libspecs) macro-prefix)]
    (when
     (or
         ;; locally defined macro
      (and (= current-ns macro-prefix)
           (= sym macro-suffix))
         ;; fully qualified
      (= sym macro-name)
         ;; aliased
      (when alias? (= sym (str alias? "/" macro-suffix)))
         ;; referred
      (when (macro-referred? libspecs macro-name)
        (= sym macro-suffix))
         ;; I used to have a clause here for (:use .. :rename {...})
         ;; but :use is ;; basically deprecated and nobody used :rename to
         ;; begin with so I dropped it when the test failed.
      )
      macro-name)))

(defn- active-bindings
  "Find all the bindings above the current zip-node."
  [zip-node]
  (->> (zip/leftmost zip-node)
       (iterate #(zip/up %))
       (take-while identity)
       (mapcat #(-> % zip/sexpr bindings/extract-local-bindings))
       (into #{})))

(defn- macro-shadowed? [macro-sym zip-node]
  ((active-bindings zip-node) macro-sym))

(defn- content-offset [path]
  (-> path slurp sexp/get-first-sexp str/split-lines count))

(defn- collect-occurrences
  [occurrences macros ^File path zip-node]
  (let [node (zip/node zip-node)
        macro-names (map (comp str :name) macros)
        sym (:string-value node)
        path (.getAbsolutePath path)
        ns-form (core/read-ns-form path)
        libspecs (ns-parser/get-libspecs ns-form)
        current-ns (str (second ns-form))
        offset (content-offset path)
        found-macro-name (some (partial macro-found? sym libspecs current-ns) macro-names)]
    (when (and found-macro-name (not (macro-shadowed? sym zip-node)))
      (swap! occurrences conj
             (node->occurrence path found-macro-name offset zip-node))))
  zip-node)

(defn- fully-qualified-name? [fully-qualified-name]
  (when (core/prefix fully-qualified-name)
    fully-qualified-name))

(defn- find-usages-in-file [macros ^File path]
  (let [zipper (-> path slurp core/file-content-sans-ns zip/of-string)
        occurrences (atom [])]
    (zip/postwalk zipper
                  token-node?
                  (partial collect-occurrences occurrences macros path))
    (loop [zipper (zip/right zipper)]
      (when zipper
        (zip/postwalk zipper
                      token-node?
                      (partial collect-occurrences occurrences macros path))
        (recur (zip/right zipper))))
    @occurrences))

(defn find-macro
  "Finds all occurrences of the macro, including the definition, in
  the project."
  [fully-qualified-name ignore-errors?]
  (when (fully-qualified-name? fully-qualified-name)
    (let [all-defs (find-macro-definitions-in-project ignore-errors?)
          macro-def (first (filter #(= (:name %) fully-qualified-name) all-defs))
          tracker (tracker/build-tracker (util/with-suppressed-errors
                                           (every-pred (complement util/interrupted?)
                                                       tracker/default-file-filter-predicate)
                                           ignore-errors?))
          origin-ns (symbol (core/prefix fully-qualified-name))
          dependents (tracker/get-dependents tracker origin-ns)]
      (some->> macro-def
               ^String (:file)
               File.
               (conj dependents)
               (keep (fn [x]
                       (when-not (util/interrupted?)
                         (find-usages-in-file [macro-def] x))))
               (apply concat)
               (into #{})
               (remove nil?)
               (sort-by :line-beg)))))

(defn find-used-macros
  "Finds used macros of the namespace given as `used-ns` in the file."
  [file used-ns]
  (some->> (symbol used-ns)
           ns-publics
           (filter (comp :macro meta val))
           (map val)
           (map str)
           (map core/normalize-var-name)
           set
           (map #(assoc {} :name %))
           seq
           (#(find-usages-in-file % (io/file file)))
           (map #(select-keys % [:name :line-beg :line-end :col-beg :col-end :file]))))

(defn warm-macro-occurrences-cache []
  ;noop, will be removed with release 2.2.0
  )
