(ns refactor-nrepl.ns.ns-parser
  "Extracts a list of imports or libspecs from an ns form.  A libspec
  looks like this:

  {:ns my-ns
   :as my-alias
   :refer [referred symbols here] ;; or :all
   :rename {:rename :spec}
   :only [only these symbols]

   ;; rest are cljs specific
   :refer-macros [referred macros here]
   :require-macros true}"
  (:require [refactor-nrepl.ns.helpers :refer [get-ns-component prefix-form?]]
            [refactor-nrepl.util :as util]))

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
                                   (if (prefix-form? libspec)
                                     (let [prefix (first libspec)]
                                       (map (partial prepend-prefix prefix)
                                            (rest libspec)))
                                     [libspec]))]
    (mapcat normalize-libspec-vector libspecs)))

(defn- use-to-refer-all [use-spec]
  (if (vector? use-spec)
    (if (prefix-form? use-spec)
      [(first use-spec) (map #(conj % :refer :all))]
      (conj use-spec :refer :all))
    [use-spec :refer :all]))

(defmacro with-libspecs-from
  "Bind the symbol libspecs to the libspecs extracted from the key in
  ns-form and execute body."
  [ns-form key & body]
  `(let [~'libspecs (rest (get-ns-component ~ns-form ~key))]
     ~@body))

(defn- extract-required-macros [libspec-vector]
  {:ns (first libspec-vector)
   :require-macros (drop-while #(not= :refer %) libspec-vector)})

(defn- extract-libspecs [ns-form]
  (mapcat identity
          [(with-libspecs-from ns-form :require
             (->> libspecs
                  expand-prefix-specs
                  (map libspec-vector->map)))
           (with-libspecs-from ns-form :require-macros
             (->> libspecs
                  (map libspec-vector->map)
                  (map (partial util/rename-key :refer :require-macros))))
           (with-libspecs-from ns-form :use
             (->> libspecs
                  expand-prefix-specs
                  (map use-to-refer-all)
                  (map libspec-vector->map)))
           (with-libspecs-from ns-form :use-macros
             (->> libspecs
                  (map libspec-vector->map)
                  (map (partial util/rename-key :only :require-macros))))]))

(defn get-libspecs [ns-form]
  (->> ns-form
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
    (some->> (get-ns-component ns-form :import)
             rest ; drop :import
             (map expand-prefix-specs)
             flatten
             distinct)))
