(ns refactor-nrepl.util
  (:require [clojure.tools.namespace.find :refer [find-clojure-sources-in-dir]]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.string :as str]
            [clojure.set :as set])
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

(defn- search-sexp-boundary
  "Searches sexp open or close of depth depending on the passed in pred.

   If search is to be done backwards pass the string in reversed."
  ([pred s]
   (let [open #{\[ \{ \(}
         close #{\] \} \)}]
     (if (some (set/union open close) s)
       (loop [chars s
              cnt 0
              op-cl 0
              ready false]
         (let [[ch & rest-chars] chars]
           (cond (and ready (pred op-cl))
                 (if (and (= (nth s cnt) \#)
                          (= (nth s (dec cnt) \{)))
                   (inc cnt)
                   cnt)

                 (open ch)
                 (recur rest-chars (inc cnt) (inc op-cl) true)

                 (close ch)
                 (recur rest-chars (inc cnt) (dec op-cl) true)

                 :else
                 (recur rest-chars (inc cnt) op-cl ready))))
       0))))

(defn- read-first-form [form]
  (let [f-string (str form)]
    (when (some #{\) \} \]} f-string)
      (-> f-string
          read-string))))

(defn get-enclosing-sexp
  "Extracts the sexp enclosing point at LINE and COLUMN in FILE-CONTENT.

  A string is not treated as a sexp by this function.

  Line is indexed from 1, and column is indexed from 0 (this is how
  emacs does it)."
  [file-content line column]
  (let [lines (str/split-lines file-content)
        line-index (dec line)
        char-count-for-lines (->> lines
                                  (take line-index)
                                  (map count)
                                  (reduce + line-index))
        content-to-point (-> char-count-for-lines
                             (+ column)
                             (take file-content))
        sexp-start (->> content-to-point
                        reverse
                        (search-sexp-boundary (partial < 0))
                        (- (count content-to-point)))
        content-from-sexp (.substring file-content sexp-start)
        sexp-end (+ sexp-start
                    (->> content-from-sexp
                         (search-sexp-boundary (partial = 0))))]
    (.substring file-content sexp-start sexp-end)))

(defn node-for-sexp?
  "Is NODE the ast node for SEXP?"
  [sexp node]
  (binding [*read-eval* false]
    (let [sexp-sans-comments-and-meta (read-string sexp)
          pattern (re-pattern (Pattern/quote (str sexp-sans-comments-and-meta)))]
      (if-let [forms (:raw-forms node)]
        (some #(re-find pattern %) (map (comp str read-first-form) forms))
        (= sexp-sans-comments-and-meta (read-first-form (:form node)))))))
