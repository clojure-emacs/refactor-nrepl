(ns refactor-nrepl.s-expressions
  (:require [rewrite-clj.zip :as zip]))

(defn all-zlocs
  "Generate a seq of all zlocs in a depth-first manner"
  [zipper]
  (take-while (complement zip/end?) (iterate zip/next zipper)))

(defn- comment-or-string-or-nil? [zloc]
  (or (nil? zloc)
      (not (zip/sexpr zloc)) ; comment node
      (string? (zip/sexpr zloc))))

(defn get-next-sexp [file-content]
  (let [zloc (zip/of-string file-content)]
    (some (fn [zloc] (when-not (comment-or-string-or-nil? zloc)
                       (zip/string zloc)))
          (take-while (complement nil?) (iterate zip/right zloc)))))

(defn get-last-sexp [file-content]
  (let [zloc (->> file-content zip/of-string zip/rightmost)]
    (some (fn [zloc] (when-not (comment-or-string-or-nil? zloc)
                       (zip/string zloc)))
          (take-while (complement nil?) (iterate zip/left zloc)))))

(defn- zip-to
  "Move the zipper to the node at line and col"
  [zipper line col]
  (let [distance (fn [zloc]
                   (let [node (zip/node zloc)
                         line-beg (dec (:row (meta node)))
                         line-end (dec (:end-row (meta node)))
                         col-beg (dec (:col (meta node)))
                         col-end (dec (:end-col (meta node)))]
                     (+ (* 1000 (Math/abs (- line line-beg)))
                        (* 100 (Math/abs (- line line-end)))
                        (* 10 (Math/abs (- col col-beg)))
                        (Math/abs (- col col-end)))))]
    (reduce (fn [best zloc] (if (< (distance zloc) (distance best))
                              zloc
                              best))
            zipper
            (all-zlocs zipper))))

(defn get-enclosing-sexp
  "Extracts the sexp enclosing point at LINE and COLUMN in FILE-CONTENT,
  and optionally LEVEL.

  A string is not treated as a sexp by this function. If LEVEL is
  provided finds the enclosing sexp up to level. LEVEL defaults to 1
  for the immediate enclosing sexp.

  Both line and column are indexed from 0."
  ([file-content line column]
   (get-enclosing-sexp file-content line column 1))
  ([file-content line column level]
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
