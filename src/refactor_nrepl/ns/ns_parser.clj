(ns refactor-nrepl.ns.ns-parser
  "Extracts a list of imports or libspecs from an ns form.  A libspec
  looks like this:

  {:ns my-ns
   :as my-alias
   :refer [referred symbols here] ; or :all
   :rename {:rename :spec}
   :only [only these symbols]

   ;; rest are cljs specific
   :refer-macros [referred macros here]
   :require-macros true}"
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [refactor-nrepl.core :as core])
  (:import
   (java.io File)))

(defn- libspec-vector->map
  [libspec]
  (if (vector? libspec)
    (let [[ns & specs] libspec]
      (into {:ns ns} (->> specs (partition 2) (map vec))))
    {:ns (symbol libspec)}))

(defn- expand-prefix-specs
  "Eliminate prefix lists."
  [libspecs]
  (let [prepend-prefix (fn add-prefix [prefix libspec]
                         (if (sequential? libspec)
                           (apply vector
                                  (symbol (str prefix "." (first libspec)))
                                  (rest libspec))
                           (symbol (str prefix "." libspec))))
        normalize-libspec-vector (fn [libspec]
                                   (if (core/prefix-form? libspec)
                                     (let [prefix (first libspec)]
                                       (map (partial prepend-prefix prefix)
                                            (rest libspec)))
                                     [libspec]))]
    (mapcat normalize-libspec-vector libspecs)))

(defn- use-to-refer-all [use-spec]
  (if (vector? use-spec)
    (if (core/prefix-form? use-spec)
      [(first use-spec) (map #(conj % :refer :all))]
      (conj use-spec :refer :all))
    [use-spec :refer :all]))

(defmacro with-libspecs-from
  "Bind the symbol libspecs to the libspecs extracted from the key in
  ns-form and execute body."
  [ns-form key & body]
  `(let [~'libspecs (rest (core/get-ns-component ~ns-form ~key))]
     ~@body))

(defn- extract-libspecs [ns-form]
  (mapcat identity
          [(with-libspecs-from ns-form :require
             (->> libspecs
                  expand-prefix-specs
                  (map libspec-vector->map)))

           (with-libspecs-from ns-form :use
             (->> libspecs
                  expand-prefix-specs
                  (map use-to-refer-all)
                  (map libspec-vector->map)))]))

(defn get-libspecs [ns-form]
  (some->> ns-form
           extract-libspecs
           distinct))

(defn get-imports [ns-form]
  (let [expand-prefix-specs (fn [import-spec]
                              (if (sequential? import-spec)
                                (let [package (first import-spec)]
                                  (map (fn [class-name]
                                         (symbol (str package "." class-name)))
                                       (rest import-spec)))
                                import-spec))]
    (some->> (core/get-ns-component ns-form :import)
             rest ; drop :import
             (map expand-prefix-specs)
             flatten
             distinct)))

(defn get-required-macros [ns-form]
  (into (with-libspecs-from ns-form :require-macros
          (->> libspecs
               (map libspec-vector->map)))
        (with-libspecs-from ns-form :use-macros
          (->> libspecs
               (map libspec-vector->map)
               (map #(set/rename-keys % {:only :refer}))))))

(defn- parse-clj-or-cljs-ns
  ([path] (parse-clj-or-cljs-ns path nil))
  ([path dialect]
   (let [dialect (or dialect (core/file->dialect path))
         ns-form (core/read-ns-form-with-meta dialect path)]
     {dialect (merge {:require (get-libspecs ns-form)
                      :import (get-imports ns-form)}
                     (when (= dialect :cljs)
                       {:require-macros (get-required-macros ns-form)}))})))

(defn- parse-cljc-ns [path]
  (merge (parse-clj-or-cljs-ns path :clj)
         (parse-clj-or-cljs-ns path :cljs)))

(defn parse-ns [path-or-file]
  (let [[_ namespace-name :as ns-form] (core/read-ns-form-with-meta path-or-file)
        select-metadata (juxt :top-level-meta :attr-map)
        metadata (-> ns-form meta select-metadata)]
    (assoc
     (if (core/cljc-file? (io/file path-or-file))
       (parse-cljc-ns path-or-file)
       (parse-clj-or-cljs-ns path-or-file))
     :ns namespace-name
     :meta (apply merge metadata)
     :source-dialect (core/file->dialect path-or-file))))

(def ^:dynamic *read-ns-form-with-meta* core/read-ns-form-with-meta)

(alter-meta! #'*read-ns-form-with-meta* merge (-> core/read-ns-form-with-meta var meta (select-keys [:doc :arglists])))

(defn get-libspecs-from-file
  "Return all the libspecs in a file.

  This is the concatenation of all the libspecs found in the use,
  use-macros, require and require-macros forms.

  Note that no post-processing is done so there might be duplicates or
  libspecs which could have been combined or eliminated as unused.

  Dialect is either :clj or :cljs, the default is :clj."
  ([^File f]
   (get-libspecs-from-file :clj f))
  ([dialect ^File f]
   (some->> f
            .getAbsolutePath
            (*read-ns-form-with-meta* dialect)
            ((juxt get-libspecs get-required-macros))
            (mapcat identity))))

(defn aliases
  "Return a map of namespace aliases given a seq of libspecs.

  e.g {str clojure.string}"
  [libspecs]
  (->> libspecs
       (keep (fn alias [{:keys [ns as as-alias]}]
               (cond-> {}
                 as (assoc as ns)
                 as-alias (assoc as-alias ns))))
       (apply merge)))
