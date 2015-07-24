(ns refactor-nrepl.ns.ns-parser
  "Extracts a list of imports or libspecs from an ns form.  A libspec
  looks like this:

  {:ns ns-name
   :as alias
   :refer [referred symbols here] ;; or :all
   :rename {:rename :spec}
   :only [only these symbols]}"
  (:require [refactor-nrepl.ns.helpers :refer [get-ns-component prefix-form?]]))

(defn- process-libspec
  [libspec]
  (if (vector? libspec)
    (let [[ns & specs] libspec
          extract-spec (fn [spec](->> specs (drop-while #(not= % spec)) second))]
      (into {:ns ns} (map vec (partition 2 specs))))
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

(defn- use-to-refer-all [use-specs]
  (map (fn [use-spec] (if (vector? use-spec)
                        (if (prefix-form? use-spec)
                          [(first use-spec) (map #(conj % :refer :all))]
                          (conj use-spec :refer :all))
                        [use-spec :refer :all]))
       use-specs))

(defn get-libspecs [ns-form]
  (->> (get-ns-component ns-form :use)
       rest ; drop :use keyword
       use-to-refer-all
       (into (rest (get-ns-component ns-form :require)))
       expand-prefix-specs
       (map process-libspec)
       distinct))

(defn get-imports [ns-form]
  (let [expand-prefix-specs (fn [import-spec]
                              (if (vector? import-spec)
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
