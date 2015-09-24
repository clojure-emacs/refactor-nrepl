(ns refactor-nrepl.util
  (:require [clojure.java.classpath :as cp]
            [clojure.java.io :as io]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [clojure.tools.namespace
             [find :as find]
             [parse :refer [read-ns-decl]]]
            [clojure.walk :as walk]
            [me.raynes.fs :as fs]
            [rewrite-clj.zip :as zip])
  (:import [java.io File PushbackReader]
           java.util.regex.Pattern))


(defn normalize-to-unix-path
  "Replace use / as separator and lower-case."
  [path]
  (if (.contains (System/getProperty "os.name") "Windows")
    (.replaceAll path (Pattern/quote "\\") "/")
    path))

(defn dirs-on-classpath* []
  (->> (cp/classpath)
       (filter fs/directory?)
       (remove #(-> % str (.endsWith "target/srcdeps")))))

(defn dirs-on-classpath
  "Return all directories on classpath."
  []
  (->> (dirs-on-classpath*)
       (map #(.getAbsolutePath %))
       (map normalize-to-unix-path)))

(defn find-clojure-sources-in-project
  "Return all clojure files in the project that are on the classpath."
  []
  (mapcat find/find-sources-in-dir (dirs-on-classpath*)))

(defn find-in-dir
  "Searches recursively under dir for files matching (pred ^File file). "
  [pred ^File dir]
  (filter pred (file-seq dir)))

(defn cljc-file?
  [path-or-file]
  (.endsWith (.getPath (io/file path-or-file)) ".cljc"))

(defn cljs-file?
  [path-or-file]
  (.endsWith (.getPath (io/file path-or-file)) ".cljs"))

(defn clj-file?
  [path-or-file]
  (.endsWith (.getPath (io/file path-or-file)) ".clj"))

(defn source-file?
  "True for clj, cljs or cljc files."
  [path-or-file]
  ((some-fn cljc-file? cljs-file? clj-file?) (io/file path-or-file)))


(defn file->dialect
  "Return the clojure dialect used in the file f.

  The dialect is either :clj, :cljs or :cljc."
  [path-or-file]
  (let [f (io/file path-or-file)]
    (cond
      (clj-file? f) :clj
      (cljs-file? f) :cljs
      (cljc-file? f) :cljc
      :else (throw (ex-info "Path isn't pointing to file in a clj dialect!"
                            {:path path-or-file})))))

(defn filter-project-files
  "Return the files in the project satisfying (pred ^File file)."
  [pred]
  (mapcat (partial find-in-dir pred) (dirs-on-classpath*)))

(defn node-at-loc? [loc-line loc-column node]
  (let [{:keys [line end-line column end-column]} (:env node)]
    ;; The node for ::an-ns-alias/foo, when it appeared as a toplevel form,
    ;; had nil as position info
    (and line end-line column end-column
         (and (>= loc-line line)
              (<= loc-line end-line)
              (>= loc-column column)
              (<= loc-column end-column)))))

(defn top-level-form-index
  [line column ns-ast]
  (->> ns-ast
       (map-indexed #(vector %1 (->> %2
                                     nodes
                                     (some (partial node-at-loc? line column)))))
       (filter #(second %))
       ffirst))

(defn throw-unless-clj-file [file-path]
  (when-not (re-matches #".+\.clj$" file-path)
    (throw (IllegalArgumentException.
            "Only .clj files are supported!"))))

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

(defn zip-to
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

(defn re-pos
  "Map of regexp matches and their positions keyed by positions."
  [re s]
  (loop [m (re-matcher re s)
         res (sorted-map)]
    (if (.find m)
      (recur m (assoc res (.start m) (.group m)))
      res)))

(defn filter-map
  "Return a new map where (pred [k v]) is true for every key-value pair."
  [pred m]
  (into {} (filter pred m)))

(defn dissoc-when
  "Remove the enumerated keys from m on which pred is truthy."
  [m pred & ks]
  (if (seq ks)
    (reduce (fn [m k] (if (pred (get m k)) (dissoc m k) m)) m ks)
    m))

(defn ex-info-assoc
  "Assoc kvs onto e's data map."
  [^clojure.lang.ExceptionInfo e & kvs]
  (ex-info (.getMessage e) (apply assoc (ex-data e) kvs) (.getCause e)))

(defmacro with-additional-ex-data
  "Execute body and if an ex-info is thrown, assoc kvs onto the data
  map and rethrow."
  [kvs & body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (throw (apply ex-info-assoc e# ~kvs)))))

(defn conj-some
  "Like conj but nil values are discared from xs."
  [coll & xs]
  (let [xs (remove nil? xs)]
    (if (seq xs)
      (apply conj coll xs)
      coll)))
