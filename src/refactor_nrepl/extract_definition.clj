(ns refactor-nrepl.extract-definition
  (:require [clojure.string :as str]
            [refactor-nrepl
             [find-symbol :refer [create-result-alist find-symbol]]
             [util :refer [get-enclosing-sexp]]]
            [refactor-nrepl.ns.helpers :refer [suffix]]
            [refactor-nrepl.util :refer [get-enclosing-sexp get-next-sexp]])
  (:import [java.io PushbackReader StringReader]
           java.util.regex.Pattern))

(defn- occurrence-to-map
  [occurrence]
  (zipmap (take-nth 2 occurrence) (take-nth 2 (rest occurrence))))

(defn- extract-definition-from-def
  [^String sexp]
  (let [def-form (read-string sexp)
        docstring? (string? (nth def-form 2 :not-found))
        def-of-literal? ()
        sexp-sans-delimiters (.substring (str/trim sexp) 1 (dec (.length sexp)))
        rdr (PushbackReader. (StringReader. sexp-sans-delimiters))]
    (read rdr) ; discard def
    (read rdr) ; discard var name
    (when docstring?
      (read rdr)) ; discard docstring
    (str/trim (slurp rdr))))

(defn- extract-definition-from-defn
  [^String sexp]
  (let [form (read-string sexp)
        fn-name (str (second form))]
    (-> sexp
        (.replaceFirst (if (re-find #"defn-" sexp) "defn-" "defn") "fn")
        (.replaceFirst (str "\\s*" (Pattern/quote fn-name)) ""))))

(defn- extract-def-from-binding-vector
  [^String bindings ^String var-name]
  (let [i (.indexOf bindings var-name)
        next-form-with-trailing-garb (str/trim
                                      (.substring bindings (+ i (.length var-name))))
        next-form (read-string next-form-with-trailing-garb)]
    (if (re-find #"[\({\[]" (str next-form))
      (get-next-sexp next-form-with-trailing-garb)
      (str next-form))))

(defn- -extract-definition
  [{:keys [match file line-beg col-beg name]}]
  (let [literal-sexp (get-enclosing-sexp (slurp file) line-beg col-beg)
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
      false)))

(defn- def?
  "Is the OCCURRENCE the defining form?"
  [{:keys [file name col-beg line-beg] :as msg}]
  (let [form (read-string (get-enclosing-sexp (slurp file) line-beg col-beg))
        name (symbol (suffix (read-string name)))]
    (if (def-form? form)
      (= (second form) name)
      (when (vector? form)
        (> (count (drop-while #(not= % name) form)) 1)))))

(defn extract-definition
  "Returns the definition of SYMBOL to facilitate inlining."
  [msg]
  (let [occurrences (some->> msg
                             find-symbol
                             (map #(apply create-result-alist %))
                             (map occurrence-to-map))]
    (when-let [definition-occurrence (some->> occurrences
                                              (filter def?)
                                              first)]
      {:definition (merge {:definition (-extract-definition definition-occurrence)}
                          definition-occurrence)
       :occurrences (remove #(= % definition-occurrence) occurrences)})))
