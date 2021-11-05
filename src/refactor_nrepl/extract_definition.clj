(ns refactor-nrepl.extract-definition
  (:require
   [clojure.string :as str]
   [refactor-nrepl.core :refer [suffix]]
   [refactor-nrepl.find.find-symbol :refer [find-symbol]]
   [refactor-nrepl.s-expressions :as sexp]
   [rewrite-clj.zip :as zip])
  (:import
   (java.io PushbackReader StringReader)
   (java.util.regex Pattern)))

(defn- extract-definition-from-def
  ^String [^String sexp]
  (let [def-form (read-string sexp)
        docstring? (string? (nth def-form 2 :not-found))
        sexp-sans-delimiters (.substring (str/trim sexp) 1 (dec (.length sexp)))
        rdr (PushbackReader. (StringReader. sexp-sans-delimiters))]
    (read rdr) ; discard def
    (read rdr) ; discard var name
    (when docstring?
      (read rdr)) ; discard docstring
    (str/trim (slurp rdr))))

(defn- extract-definition-from-defn
  ^String [^String sexp]
  (let [form (read-string sexp)
        fn-name (str (second form))]
    (-> sexp
        (.replaceFirst (if (re-find #"defn-" sexp) "defn-" "defn") "fn")
        (.replaceFirst (str "\\s*" (Pattern/quote fn-name)) ""))))

(defn- extract-def-from-binding-vector
  ^String [^String bindings ^String var-name]
  (let [zipper (zip/of-string bindings)
        zloc (some (fn [zloc] (when (= (symbol var-name) (zip/sexpr zloc)) zloc))
                   (sexp/all-zlocs zipper))]
    (when zloc
      (str/trim (zip/string (zip/right zloc))))))

(defn- -extract-definition
  [{:keys [file ^long line-beg ^long col-beg name]}]
  (let [literal-sexp (sexp/get-enclosing-sexp (slurp file) (dec line-beg)
                                              col-beg)
        form (read-string literal-sexp)]
    (.replaceAll
     (case (first form)
       def (extract-definition-from-def literal-sexp)
       def- (extract-definition-from-def literal-sexp)
       defn (extract-definition-from-defn literal-sexp)
       defn- (extract-definition-from-defn literal-sexp)
       (extract-def-from-binding-vector literal-sexp name))
     "\r" "")))

(defn- def-form?
  "Is FORM a def or defn?"
  [form]
  (if (and (or (list? form) (instance? clojure.lang.Cons form)) (seq form))
    (case (first form)
      def true
      def- true
      defn true
      defn- true
      false)
    false))

(defn- def?
  "Is the OCCURRENCE the defining form?"
  [{:keys [file name ^long col-beg ^long line-beg]}]
  (let [form (read-string (sexp/get-enclosing-sexp (slurp file) (dec line-beg)
                                                   col-beg))
        name (symbol (suffix (read-string name)))]
    (if (def-form? form)
      (= (second form) name)
      (when (vector? form)
        (> (count (drop-while #(not= % name) form)) 1)))))

(defn- sort-by-linum
  [occurrences]
  (sort #(- (long (:line-beg %1)) (long (:line-beg %2))) occurrences))

(defn- find-definition [occurrences]
  (some->> occurrences
           (filter def?)
           ;; When working with let-like bindings we have to sort the
           ;; occurrences so the ones earlier in the file comes first
           sort-by-linum
           first))

(defn extract-definition
  "Returns the definition of SYMBOL to facilitate inlining."
  [msg]
  (let [occurrences (find-symbol msg)]
    (if-let [definition-occurrence (find-definition occurrences)]
      {:definition (merge {:definition (-extract-definition definition-occurrence)}
                          definition-occurrence)
       :occurrences (remove (partial = definition-occurrence) occurrences)}
      (throw (IllegalStateException.
              (str "Couldn't find definition for " (:name msg)))))))
