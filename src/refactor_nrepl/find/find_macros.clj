(ns refactor-nrepl.find.find-macros
  (:import clojure.lang.LineNumberingPushbackReader
           [java.io BufferedReader File FileReader StringReader])
  (:require [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [refactor-nrepl.find.bindings :as bindings]
            [refactor-nrepl.ns
             [helpers :as ns-helpers]
             [ns-parser :as ns-parser]
             [tracker :as tracker]]
            [refactor-nrepl.util :as util]
            [rewrite-clj.zip :as zip]))

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
  [form path]
  (let [{:keys [line column end-line end-column]} (meta form)
        file-content (slurp path)
        file-ns (util/ns-from-string file-content)
        content (keep-lines file-content end-line)
        sexp (util/get-last-sexp content)
        macro-name (name (second form))
        col-beg (dec (.indexOf sexp macro-name))]
    {:name (str file-ns "/" macro-name)
     :col-beg column
     :col-end end-column
     :line-beg line
     :line-end end-line
     :file (.getAbsolutePath path)
     :match sexp}))

(defn- find-macro-definitions-in-file
  [path]
  (with-open [file-rdr (FileReader. path)]
    (binding [*ns* (or (ns-helpers/path->namespace path :no-error) *ns*)]
      (let [rdr (LineNumberingPushbackReader. file-rdr)]
        (loop [macros [], form (reader/read rdr nil :eof)]
          (cond
            (= form :eof) macros
            (and (sequential? form) (= (first form) 'defmacro))
            (recur (conj macros (build-macro-meta form path))
                   (reader/read rdr nil :eof))
            :else (recur macros (reader/read rdr nil :eof))))))))

(defn- find-macro-definitions-in-project
  "Finds all macros that are defined in the project."
  []
  (->> (util/find-clojure-sources-in-project)
       (mapcat find-macro-definitions-in-file)))

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
           (some #(= % (ns-helpers/suffix macro-name)))))

(defn- referred-from-use?
  [libspec macro-name]
  (some->> libspec
           :use
           (map str)
           (filter #(= % (ns-helpers/suffix macro-name)))
           first))

(defn- macro-referred? [libspecs macro-name]
  (let [libspec (some->> libspecs
                         (filter #(= (str (:ns %)) (ns-helpers/prefix macro-name)))
                         first)]
    (or (referred-from-require? libspec macro-name)
        (referred-from-use? libspec macro-name))))

(defn- node->occurrence
  "line-offset is the offset in the file where we start searching.
  This is after the ns but clj-rewrite keeps tracking of any following
  whitespace."
  [path macro-name line-offset zip-node]
  (let [node (zip/node zip-node)
        val (:string-value node)
        {:keys [row col]} (meta node)]
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
  [sym macro-name libspecs current-ns]
  (let [macro-prefix (ns-helpers/prefix macro-name)
        macro-suffix (ns-helpers/suffix macro-name)
        alias? ((get-ns-aliases libspecs) macro-prefix)]
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
     )))

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
  (-> path slurp util/get-next-sexp str/split-lines count))

(defn- collect-occurrences
  [occurrences macro ^File path zip-node]
  (let [node (zip/node zip-node)
        macro-name (str (:name macro))
        sym (:string-value node)
        path (.getAbsolutePath path)
        ns-form (ns-helpers/read-ns-form path)
        libspecs (ns-parser/get-libspecs ns-form)
        current-ns (str (second ns-form))
        offset (content-offset path)]
    (when (and (macro-found? sym macro-name libspecs current-ns)
               (not (macro-shadowed? sym zip-node)))
      (swap! occurrences conj
             (node->occurrence path macro-name offset zip-node))))
  zip-node)

(defn- find-usages-in-file [macro ^File path]
  (let [zipper (-> path slurp ns-helpers/file-content-sans-ns zip/of-string)
        occurrences (atom [])]
    (zip/postwalk zipper
                  token-node?
                  (partial collect-occurrences occurrences macro path))
    (loop [zipper (zip/right zipper)]
      (when zipper
        (zip/postwalk zipper
                      token-node?
                      (partial collect-occurrences occurrences macro path))
        (recur (zip/right zipper))))
    @occurrences))


(defn- fully-qualified-name? [fully-qualified-name]
  (when (ns-helpers/prefix fully-qualified-name)
    fully-qualified-name))

(defn find-macro
  "Finds all occurrences of the macro, including the definition, in
  the project."
  [fully-qualified-name]
  (when (and
         ;; Fail gracefully instead of blowing up with reader errors
         ;; when project contains cljc files until we had proper
         ;; support
         (empty? (util/find-cljc-files-in-project))
         (fully-qualified-name? fully-qualified-name))
    (let [all-defs (find-macro-definitions-in-project)
          macro-def (first (filter #(= (:name %) fully-qualified-name) all-defs))
          tracker (tracker/build-tracker)
          origin-ns (symbol (ns-helpers/prefix fully-qualified-name))
          dependents (tracker/get-dependents tracker origin-ns)]
      (some->> macro-def
               :file
               File.
               (conj dependents)
               (mapcat (partial find-usages-in-file macro-def))
               (into #{})
               (remove nil?)
               (sort-by :line-beg)))))
