(ns refactor-nrepl.util
  (:require [clojure.tools.namespace.find :refer [find-clojure-sources-in-dir]]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.string :as str])
  (:import java.io.PushbackReader
           java.util.regex.Pattern))

(defn alias-info [full-ast]
  (-> full-ast first :alias-info))

(defn ns-from-string [ns-string]
  (second (read-ns-decl (PushbackReader. (java.io.StringReader. ns-string)))))

(defn list-project-clj-files [dir]
  (find-clojure-sources-in-dir (java.io.File. dir)))

(defn node-at-loc? [loc-line loc-column node]
  (let [env (:env node)]
    (and (>= loc-line (:line env))
         (<= loc-line (:end-line env))
         (>= loc-column (:column env))
         (<= loc-column (:end-column env)))))

(defn top-level-form-index
  [line column ns-ast]
  (->> ns-ast
       (map-indexed #(vector %1 (->> %2
                                     nodes
                                     (some (partial node-at-loc? line column)))))
       (filter #(second %))
       ffirst))

(defn throw-unless-clj-file [file]
  (when (and file (not-empty file) (or (.endsWith file ".cljs") (.endsWith file ".cljx")))
    (throw (IllegalArgumentException.
            "Refactor nrepl doesn't work on cljs or cljx files!"))))

(defn- read-first-form [form]
  (let [f-string (str form)]
    (when (some #{\) \} \]} f-string)
      (binding [*read-eval* false]
        (-> f-string
            read-string)))))

(defn node-for-sexp?
  "Is NODE the ast node for SEXP?"
  [sexp node]
  (let [sexp-sans-comments-and-meta (read-string sexp)
        pattern (re-pattern (Pattern/quote (str sexp-sans-comments-and-meta)))]
    (if-let [forms (:raw-forms node)]
      (some #(re-find pattern %) (map (comp str read-first-form) forms))
      (= sexp-sans-comments-and-meta (read-first-form (:form node))))))

(defn- get-last-sexp
  "Read and return the last sexp in FILE-CONTENT"
  [file-content]
  (let [open #{\( \[ \{}
        close #{\) \] \}}]
    ;; Reverse the remaining content of the file
    ;; Put the last char, which is a closing delimiter, into SEXP and
    ;; drop it from TOKS.
    ;; Keep reading into SEXP until depth reaches 0.
    ;; Finally reverse SEXP, turn it into a string and read the
    ;; containing sexp.
    (loop [sexp [(first (reverse file-content))], depth 1,
           toks (seq (rest (reverse file-content)))]
      (cond
        (not (seq toks))(throw (IllegalStateException. "Unbalanced region!"))
        (= depth 0) (let [next-token (first toks)]
                      (if (= next-token \#)
                        (apply str "#" (reverse sexp))
                        (apply str (reverse sexp))))

        (get open (first toks))
        (recur (conj sexp (first toks)) (dec depth) (rest toks))

        (get close (first toks))
        (recur (conj sexp (first toks)) (inc depth) (rest toks))

        :else
        (recur (conj sexp (first toks)) depth (rest toks))))))

(defn get-enclosing-sexp
  "Extracts the sexp enclosing point at LINE and COLUMN in FILE-CONTENT.

  A string is not treated as a sexp by this function.

  Line is indexed from 1, and column is indexed from 0 (this is how
  emacs does it)."
  [file-content line column]
  (let [open #{\( \[ \{}
        close #{\) \] \}}
        after-point? (fn [current-line current-column]
                       (or (and (= current-line line)
                                (> current-column column))
                           (> current-line line)))]
    ;; We read everything into read-so-far until we've read up to line
    ;; and column.

    ;; Then we start keeping track of the depth and read until it reaches 0.
    ;; Then we get-last-sexp to read out the last sexp in the content
    ;; we've read so far.
    (loop [current-line 1, current-column 0, depth-after-point 1
           read-so-far [], toks (seq file-content)]
      (cond
        (not (seq toks)) (throw (IllegalStateException. "Unbalanced region!"))
        (and (after-point? current-line current-column)
             (= depth-after-point 0))
        (get-last-sexp read-so-far)

        (get open (first toks))
        (if (after-point? current-line current-column)
          (recur current-line (inc current-column) (inc depth-after-point)
                 (conj read-so-far (first toks)) (rest toks))
          (recur current-line (inc current-column) depth-after-point
                 (conj read-so-far (first toks)) (rest toks)))

        (get close (first toks))
        (if (after-point? current-line current-column)
          (recur current-line (inc current-column) (dec depth-after-point)
                 (conj read-so-far (first toks)) (rest toks))
          (recur current-line (inc current-column) depth-after-point
                 (conj read-so-far (first toks)) (rest toks)))

        (= (first toks) \newline)
        (recur (inc current-line) 0 depth-after-point
               (conj read-so-far (first toks)) (rest toks))

        :else (recur current-line (inc current-column) depth-after-point
                     (conj read-so-far (first toks)) (rest toks))))))
