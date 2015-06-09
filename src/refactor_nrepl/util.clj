(ns refactor-nrepl.util
  (:require [clojure
             [set :as set]
             [string :as str]
             [walk :as walk]]
            [clojure.java.classpath :as cp]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [clojure.tools.namespace
             [find :refer [find-clojure-sources-in-dir]]
             [parse :refer [read-ns-decl]]]
            [me.raynes.fs :as fs])
  (:import java.io.PushbackReader
           java.util.regex.Pattern))

(defn alias-info [full-ast]
  (-> full-ast first :alias-info))

(defn ns-from-string [ns-string]
  (second (read-ns-decl (PushbackReader. (java.io.StringReader. ns-string)))))

(defn normalize-to-unix-path
  "Replace use / as separator and lower-case."
  [path]
  (.toLowerCase
   (if (.contains (System/getProperty "os.name") "Windows")
     (.replaceAll path (Pattern/quote "\\") "/")
     path)))

(defn dirs-on-classpath
  "Return all directories on classpath."
  []
  (->> (cp/classpath) (filter fs/directory?)
       (map #(.getAbsolutePath %))
       (map normalize-to-unix-path)))

(defn find-clojure-sources-in-project
  "Return all clojure files in the project that are on the classpath."
  []
  (let [dirs-on-cp (filter fs/directory? (cp/classpath))]
    (mapcat find-clojure-sources-in-dir dirs-on-cp)))

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

(defn- normalize-anon-fn-params
  "replaces anon fn params in a read form"
  [form]
  (walk/postwalk
   (fn [token] (if (re-matches #"p\d+__\d+#" (str token)) 'p token)) form))

(defn- read-first-form [form]
  (let [f-string (str form)]
    (when (some #{\) \} \]} f-string)
      (read-string f-string))))

(defn node-for-sexp?
  "Is NODE the ast node for SEXP?"
  [sexp node]
  (binding [*read-eval* false]
    (let [sexp-sans-comments-and-meta (normalize-anon-fn-params (read-string sexp))
          pattern (re-pattern (Pattern/quote (str sexp-sans-comments-and-meta)))]
      (if-let [forms (:raw-forms node)]
        (some #(re-find pattern %)
              (map (comp str normalize-anon-fn-params read-first-form) forms))
        (= sexp-sans-comments-and-meta (-> (:form node)
                                           read-first-form
                                           normalize-anon-fn-params))))))

(defn- search-sexp-boundary
  "Searches for open or closing delimiter of given depth.

  Depth depends on the passed in pred.

  Searches forward by default, if backwards passed in searches backwards.

  If pred is (partial = 0) returns the index of the closing delimiter
  of the first s-expression. If pred is (partial < 0) searches for
  the opening paren of the first sexp."
  ([pred s]
   (search-sexp-boundary pred nil s))
  ([pred backwards s]
   (let [open #{\[ \{ \(}
         close #{\] \} \)}
         s (if backwards (reverse s) s)
         prev-char-fn (if backwards inc dec)]
     (if (some (set/union open close) s)
       (loop [chars s
              cnt 0
              op-cl 0
              ready false]
         (let [[ch & chars] chars
               char-index (dec cnt)]
           (cond (and ready (pred op-cl))
                 (if (and (= (nth s char-index) \{)
                          (not= 0 char-index)
                          (= (nth s (prev-char-fn char-index)) \#))
                   (prev-char-fn char-index)
                   char-index)

                 (open ch)
                 (recur chars (inc cnt) (inc op-cl) true)

                 (close ch)
                 (recur chars (inc cnt) (dec op-cl) true)

                 (> cnt (count s)) (throw (IllegalArgumentException.
                                           "Can't find sexp boundary!"))

                 :else
                 (recur chars (inc cnt) op-cl ready))))
       0))))

(defn- cut-sexp [code sexp-start sexp-end]
  (if (= sexp-start sexp-end)
    ""
    (.substring code sexp-start (inc sexp-end))))

(defn get-next-sexp [code]
  (let [trimmed-code (str/trim code)
        sexp-start (search-sexp-boundary (partial < 0) trimmed-code)
        sexp-end (search-sexp-boundary (partial = 0) trimmed-code)]
    (cut-sexp trimmed-code sexp-start sexp-end)))

(defn get-enclosing-sexp
  "Extracts the sexp enclosing point at LINE and COLUMN in FILE-CONTENT.

  A string is not treated as a sexp by this function.

  Both line and column are indexed from 0."
  [file-content line column]
  (let [lines (str/split-lines file-content)
        char-count-for-lines (->> lines
                                  (take line)
                                  (map count)
                                  (reduce + line))
        content-to-point (-> char-count-for-lines
                             (+ column)
                             (take file-content))
        sexp-start (->> content-to-point
                        (search-sexp-boundary (partial < 0) :backwards)
                        (- (count content-to-point) 1))
        content-from-sexp (.substring file-content sexp-start)
        sexp-end (+ sexp-start
                    (->> content-from-sexp
                         (search-sexp-boundary (partial = 0))))]
    (cut-sexp file-content sexp-start sexp-end)))
