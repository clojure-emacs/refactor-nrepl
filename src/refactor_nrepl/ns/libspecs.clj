(ns refactor-nrepl.ns.libspecs
  (:require [refactor-nrepl.core :as core]
            [refactor-nrepl.ns.ns-parser :as ns-parser])
  (:import [java.io File]))

;; The structure here is {path {lang [timestamp value]}}
;; where lang is either :clj or :cljs
(defonce ^:private cache (atom {}))

(defn- aliases [libspecs]
  (->> libspecs
       (map #(vector (:as %) (:ns %)))
       (remove #(nil? (first %)))
       distinct))

(defn- aliases-by-frequencies [libspecs]
  (->> libspecs
       (mapcat aliases) ; => [[str clojure.string] ...]
       (sort-by (comp str second))
       (group-by first) ; => {str [[str clojure.string] [str clojure.string]] ...}
       (map (comp seq frequencies second)) ; => (([[set clojure.set] 4] [set set] 1) ...)
       (map (partial sort-by second >)) ; by decreasing frequency
       (map (partial map first)) ; drop frequencies
       (map (fn [aliases] (list (ffirst aliases) (map second aliases))))
       (mapcat identity)
       (apply hash-map)))

(defn- get-cached-libspec [^File f lang]
  (when-let [[ts v] (get-in @cache [(.getAbsolutePath f) lang])]
    (when (= ts (.lastModified f))
      v)))

(defn- put-cached-libspec [^File f lang]
  (let [libspecs (ns-parser/get-libspecs-from-file lang f)]
    (swap! cache assoc-in [(.getAbsolutePath f) lang]
           [(.lastModified f) libspecs])
    libspecs))

(defn- get-libspec-from-file-with-caching [lang f]
  (if-let [v (get-cached-libspec f lang)]
    v
    (put-cached-libspec f lang)))

(defn namespace-aliases
  "Return a map of file type to a map of aliases to namespaces

  {:clj {util com.acme.util str clojure.string
   :cljs {gstr goog.str}}}"
  []
  {:clj (->> (core/find-in-project (some-fn core/clj-file? core/cljc-file?))
             (map (partial get-libspec-from-file-with-caching :clj))
             aliases-by-frequencies)
   :cljs (->> (core/find-in-project (some-fn core/cljs-file? core/cljc-file?))
              (map (partial get-libspec-from-file-with-caching :cljs))
              aliases-by-frequencies)})

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
   the sym itself

   Example:
   {:clj  {\"/home/someuser/projects/some.clj\" [\"example.com/foobar\" foobar]}
    :cljs}"
  []
  {:clj (->> (core/find-in-project (some-fn core/clj-file? core/cljc-file?))
             (map (juxt identity (partial get-libspec-from-file-with-caching :clj)))
             sym-by-file&fullname)
   :cljs (->> (core/find-in-project (some-fn core/cljs-file? core/cljc-file?))
              (map (juxt identity (partial get-libspec-from-file-with-caching :cljs)))
              sym-by-file&fullname)})
