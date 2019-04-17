(ns refactor-nrepl.s-expressions
  (:require [rewrite-clj.parser :as zip-parser]
            [rewrite-clj.reader :as zip-reader]
            [rewrite-clj.zip :as zip]))

(defn all-zlocs
  "Generate a seq of all zlocs in a depth-first manner"
  [zipper]
  (take-while (complement zip/end?) (iterate zip/next zipper)))

(defn- comment-or-string-or-nil? [zloc]
  (or (nil? zloc)
      (not (zip/sexpr zloc)) ; comment node
      (string? (zip/sexpr zloc))))

(defn get-first-sexp
  ^String [file-content]
  (let [reader (zip-reader/string-reader file-content)]
    (loop [sexp (zip-parser/parse reader)]
      (let [zloc (zip/edn sexp)]
        (if (and zloc (not (comment-or-string-or-nil? zloc)))
          (zip/string zloc)
          (when (.peek-char reader)
            (recur (zip-parser/parse reader))))))))

(defn get-last-sexp
  ^String [file-content]
  (let [zloc (->> file-content zip/of-string zip/rightmost)]
    (some (fn [zloc] (when-not (comment-or-string-or-nil? zloc)
                       (zip/string zloc)))
          (take-while (complement nil?) (iterate zip/left zloc)))))

(defn- node-at-loc?
  "True if node encloses point defined by `loc-line` and `loc-column`."
  [zloc ^long loc-line ^long loc-column]
  (let [[line end-line column end-column] (->> (zip/node zloc)
                                               meta
                                               ((juxt :row :end-row :col :end-col))
                                               (map (comp dec long)))]
    (or (< line loc-line end-line)
        (and (or (= line loc-line)
                 (= end-line loc-line))
             (<= column loc-column end-column)))))

(defn- zip-to
  "Move the zipper to the node at `loc-line` and `loc-col`.

  Implementation uses `all-zlocs` and exploits the fact that it generates
  a seq of nodes in depth-first order."
  [zipper ^long loc-line ^long loc-column]
  (reduce
   (fn [node-at-loc zloc]
     (if (node-at-loc? zloc loc-line loc-column) zloc node-at-loc))
   zipper
   (all-zlocs zipper)))

(defn get-enclosing-sexp
  "Extracts the sexp enclosing point at LINE and COLUMN in FILE-CONTENT,
  and optionally LEVEL.

  A string is not treated as a sexp by this function. If LEVEL is
  provided finds the enclosing sexp up to level. LEVEL defaults to 1
  for the immediate enclosing sexp.

  Both line and column are indexed from 0."
  ([file-content line column]
   (get-enclosing-sexp file-content line column 1))
  ([file-content ^long line ^long column ^long level]
   (let [zloc (zip-to (zip/of-string file-content) line column)
         zloc (nth (iterate zip/up zloc) (dec level))]
     (cond
       (and zloc (string? (zip/sexpr zloc))) (zip/string (zip/up zloc))
       (and zloc (seq? (zip/sexpr zloc))) (zip/string zloc)
       zloc (zip/string (zip/up zloc))
       :else (throw (ex-info "Can't find sexp boundary"
                             {:file-content file-content
                              :line line
                              :column column}))))))
