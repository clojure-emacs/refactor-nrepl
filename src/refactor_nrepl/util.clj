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
  (:import [java.io File PushbackReader]
           java.util.regex.Pattern))

(defn alias-info [full-ast]
  (-> full-ast first :alias-info))

(defn ns-from-string [ns-string]
  (second (read-ns-decl (PushbackReader. (java.io.StringReader. ns-string)))))

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
  (mapcat find-clojure-sources-in-dir (dirs-on-classpath*)))

(defn find-in-dir
  "Searches recursively under dir for files matching (pred ^File file). "
  [pred ^File dir]
  (filter pred (file-seq dir)))

(defn cljc-file?
  [^File f]
  (.endsWith (.getPath f) ".cljc"))

(defn find-cljc-files-in-project []
  (mapcat (partial find-in-dir cljc-file?) (dirs-on-classpath*)))

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
  "Extracts the sexp enclosing point at LINE and COLUMN in FILE-CONTENT,
  and optionally LEVEL.

  A string is not treated as a sexp by this function. If LEVEL is
  provided finds the enclosing sexp up to level. LEVEL defaults to 1
  for the immediate enclising sexp.

  Both line and column are indexed from 0."
  ([file-content line column]
   (get-enclosing-sexp file-content line column 1))
  ([file-content line column level]
   (let [file-content (.replaceAll file-content "\r" "") ; \r messes up count
         lines (str/split-lines file-content)
         char-count-for-lines (->> lines
                                   (take line)
                                   (map count)
                                   (reduce + line))
         content-to-point (-> char-count-for-lines
                              (+ column)
                              (take file-content))
         sexp-start (->> content-to-point
                         (search-sexp-boundary (partial = level) :backwards)
                         (- (count content-to-point) 1))
         content-from-sexp (.substring file-content sexp-start)
         sexp-end (+ sexp-start
                     (->> content-from-sexp
                          (search-sexp-boundary (partial = 0))))]
     (cut-sexp file-content sexp-start sexp-end))))

(defn get-last-sexp [file-content]
  (let [trimmed-content (str/trim file-content)
        sexp-start (search-sexp-boundary (partial > 0) :backwards trimmed-content)
        sexp-end (search-sexp-boundary (partial = 0) :backwards trimmed-content)]
    (cut-sexp trimmed-content sexp-start sexp-end)))

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

(defn rename-key
  "Rename the key in m to new-name."
  [k new-name m]
  (-> m
      (assoc new-name (k m))
      (dissoc k)))

(defn dissoc-when
  "Remove the enumerated keys from m on which pred is truthy."
  [m pred & keys]
  (if (seq keys)
    (reduce (fn [m k] (if (pred (get m k)) (dissoc m k) m)) m keys)
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
       (throw (ex-info-assoc e# ~kvs)))))
