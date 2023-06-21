(ns refactor-nrepl.ns.libspecs
  (:require
   [refactor-nrepl.core :as core]
   [refactor-nrepl.ns.ns-parser :as ns-parser]
   [refactor-nrepl.ns.suggest-aliases :as suggest-aliases]
   [refactor-nrepl.util :as util]
   [refactor-nrepl.util.meta :as meta])
  (:import
   (java.io File)))

;; The structure here is {path {lang [timestamp value]}}
;; where lang is either :clj or :cljs
(def ^:private cache (atom {}))

(defn vec-distinct-into [x y]
  (into []
        (comp cat
              (distinct))
        [x y]))

(defn merge-libspecs-meta [a b]
  (let [{:keys [used-from files]} (meta b)]
    (cond-> a
      (seq used-from) (vary-meta update :used-from vec-distinct-into used-from)
      (seq files)     (vary-meta update :files vec-distinct-into files))))

(defn- aliases [libspecs]
  (meta/distinct merge-libspecs-meta
                 (into []
                       (comp (map (juxt :as :ns))
                             (filter first))
                       libspecs)))

(defn- aliases-by-frequencies [libspecs]
  (let [grouped (->> (into []
                           (mapcat aliases) ; => [[str clojure.string] ...]
                           libspecs)
                     (sort-by (comp str second))
                     (group-by first) ; => {str [[str clojure.string] [str clojure.string]] ...}
                     )]
    (into {}
          (comp (map (comp seq frequencies second)) ; => (([[set clojure.set] 4] [set set] 1) ...)
                (map (partial sort-by second >)) ; by decreasing frequency
                (map (partial map first))        ; drop frequencies
                (map (fn [aliases]
                       [(ffirst aliases),
                        (mapv second aliases)])))
          grouped)))

(defn- get-cached-ns-info [^File f lang]
  (when-let [[ts v] (get-in @cache [(.getAbsolutePath f) lang])]
    (when (= ts (.lastModified f))
      v)))

(defn add-used-from-meta [libspecs ^File f]
  (let [extension (case (re-find #"\.clj[cs]?$" (-> f .getAbsolutePath))
                    ".clj"  [:clj] ;; these are expressed as vectors, so that `#'merge-libspecs-meta` can operate upon them
                    ".cljs" [:cljs]
                    ".cljc" [:cljc]
                    nil)]
    (if-not extension
      libspecs
      (into []
            (map (fn [libspec]
                   (cond-> libspec
                     (not (-> libspec :ns string?))
                     (update :ns vary-meta assoc :used-from extension))))
            libspecs))))

(defn- put-cached-ns-info! [^File f lang]
  (binding [;; briefly memoize this function to avoid repeating its IO cost while `f` is being cached:
            ns-parser/*read-ns-form-with-meta* (memoize core/read-ns-form-with-meta)]
    (let [libspecs (ns-parser/get-libspecs-from-file lang f)
          [_ namespace-name] (ns-parser/*read-ns-form-with-meta* lang f)
          suggested-aliases (suggest-aliases/suggested-aliases namespace-name)
          v {:libspecs (add-used-from-meta libspecs f)
             :namespace-name namespace-name
             :suggested-aliases suggested-aliases
             :test-like-ns-name? (suggest-aliases/test-like-ns-name? namespace-name)}]
      (swap! cache
             assoc-in
             [(.getAbsolutePath f) lang]
             [(.lastModified f) v])
      v)))

(defn- get-ns-info-from-file-with-caching [lang f]
  (if-let [v (get-cached-ns-info f lang)]
    v
    (put-cached-ns-info! f lang)))

(defn- get-libspec-from-file-with-caching [lang f]
  (:libspecs (get-ns-info-from-file-with-caching lang f)))

(defn add-tentative-aliases [project-aliases lang files ignore-errors?]
  (let [aliased-namespaces (->>
                            ;; `sut` doesn't count as an alias here,
                            ;; because it is common that N namespaces can be aliased as `sut`:
                            (dissoc project-aliases 'sut)
                            vals
                            (reduce into [])
                            (set))
        non-aliased-namespaces (->> files
                                    ;; note that we don't use pmap here -
                                    ;; `files` was already iterated via `get-ns-info-from-file-with-caching`
                                    ;; by the `#'namespace-aliases` defn:
                                    (map (util/with-suppressed-errors
                                           (fn [file]
                                             (let [{:keys [namespace-name suggested-aliases test-like-ns-name?]}
                                                   (get-ns-info-from-file-with-caching lang file)
                                                   ;; if this ns is test-like, it shouldn't generate alias suggestions,
                                                   ;; otherwise clients will suggest test namespaces as a candidate for a given alias,
                                                   ;; which is never what the user means:
                                                   final-suggested-aliases (when-not test-like-ns-name?
                                                                             (not-empty suggested-aliases))]
                                               (cond-> namespace-name
                                                 final-suggested-aliases
                                                 (with-meta {:suggested-aliases final-suggested-aliases}))))
                                           ignore-errors?))
                                    (remove aliased-namespaces))
        possible-aliases (->> non-aliased-namespaces
                              (keep (comp :suggested-aliases meta))
                              (apply merge-with into))]
    (->> project-aliases
         keys
         (apply dissoc possible-aliases)
         (merge-with into project-aliases))))

;; `namespace-aliases-for` was split out from `namespace-aliases`, for a 3rd-party need.
;; `namespace-aliases-for` is a little more fine-grained, since it accepts files rather than dirs.
(defn namespace-aliases-for [files ignore-errors?]
  ;; pmap parallelizes a couple things:
  ;;   - `pred`, which is IO-intensive
  ;;   - `aliases-by-frequencies`, which is moderately CPU-intensive
  (let [[clj-aliases cljs-aliases] (pmap (fn [[dialect pred] corpus]
                                           (->> corpus
                                                (filter pred)
                                                (keep (comp (fn [v]
                                                              (or v
                                                                  ;; nullify `false` values for `keep`:
                                                                  nil))
                                                            (util/with-suppressed-errors
                                                              (partial get-libspec-from-file-with-caching dialect)
                                                              ignore-errors?)))
                                                aliases-by-frequencies))
                                         [[:clj (util/with-suppressed-errors
                                                  (some-fn core/clj-file? core/cljc-file?)
                                                  ignore-errors?)]
                                          [:cljs (util/with-suppressed-errors
                                                   (some-fn core/cljs-file? core/cljc-file?)
                                                   ignore-errors?)]]
                                         (repeat files))]
    {:clj  clj-aliases
     :cljs cljs-aliases}))

(defn namespace-aliases
  "Returns a map of file type to a map of aliases to namespaces

  {:clj {util [com.acme.util]
         str  [clojure.string]
   :cljs {gstr [goog.str]}}}"
  ([]
   (namespace-aliases false))
  ([ignore-errors?]
   (namespace-aliases ignore-errors? (core/source-dirs-on-classpath)))
  ([ignore-errors? dirs]
   (namespace-aliases ignore-errors? dirs false))
  ([ignore-errors? dirs include-tentative-aliases?]
   (let [;; fetch the file list just once (as opposed to traversing the project once for each dialect)
         files (core/source-files-with-clj-like-extension ignore-errors? dirs)
         project-aliases (namespace-aliases-for files ignore-errors?)]
     (cond-> project-aliases
       include-tentative-aliases? (update :clj  add-tentative-aliases :clj  files ignore-errors?)
       include-tentative-aliases? (update :cljs add-tentative-aliases :cljs files ignore-errors?)))))

(defn namespace-aliases-response [{:keys [suggest]}]
  (namespace-aliases true
                     (core/source-dirs-on-classpath)
                     suggest))

(defn- unwrap-refer
  [file {:keys [ns refer]}]
  (when (coll? refer)
    (map
     #(hash-map (str file) {(str ns "/" %) %})
     refer)))

(defn- apply-unwrap-refer [[file libspec]]
  (mapcat (partial unwrap-refer file) libspec))

(defn- sym-by-file&fullname [files-libspecs]
  (apply merge-with conj
         (mapcat apply-unwrap-refer files-libspecs)))

(defn referred-syms-by-file&fullname
  "Return a map of filename to a map of sym fullname to sym
   the sym itself.

   Example:
   {:clj  {\"/home/someuser/projects/some.clj\" [\"example.com/foobar\" foobar]}
    :cljs}"
  ([]
   (referred-syms-by-file&fullname false))
  ([ignore-errors?]
   ;; `pmap` is used as it has proved to be more efficient, both for cached and non-cached cases.
   {:clj  (->> (core/find-in-project (util/with-suppressed-errors
                                       (some-fn core/clj-file? core/cljc-file?)
                                       ignore-errors?)
                                     (core/source-dirs-on-classpath))
               (pmap (juxt identity (partial get-libspec-from-file-with-caching :clj)))
               sym-by-file&fullname)
    :cljs (->> (core/find-in-project (util/with-suppressed-errors
                                       (some-fn core/cljs-file? core/cljc-file?)
                                       ignore-errors?)
                                     (core/source-dirs-on-classpath))
               (pmap (juxt identity (partial get-libspec-from-file-with-caching :cljs)))
               sym-by-file&fullname)}))
