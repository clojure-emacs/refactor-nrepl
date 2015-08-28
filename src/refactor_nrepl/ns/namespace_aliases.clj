(ns refactor-nrepl.ns.namespace-aliases
  (:require [refactor-nrepl.ns
             [helpers :as ns-helpers]
             [ns-parser :as ns-parser]]
            [refactor-nrepl.util :as util]))

(defn- aliases [libspecs]
  (->> libspecs
       (map #(vector (:as %) (:ns %)))
       (remove #(nil? (first %)))
       distinct))

(defn- aliases-by-frequencies [libspecs]
  (->> libspecs
       (mapcat aliases) ; => [[str clojure.string] ...]
       (sort-by second)
       (group-by first) ; => {str [[str clojure.string] [str clojure.string]] ...}
       (map (comp seq frequencies second)) ; => (([[set clojure.set] 4] [set set] 1) ...)
       (map (partial sort-by second >)) ; by decreasing frequency
       (map (partial map first)) ; drop frequencies
       (map (fn [aliases] (list (ffirst aliases) (map second aliases))))
       (mapcat identity)
       (apply hash-map)))

(defn namespace-aliases
  "Return a map of file type to a map of aliases to namespaces

  {:clj {util com.acme.util str clojure.string
   :cljs {gstr goog.str}}}"
  []
  {:clj (->> (util/filter-project-files util/clj-file?)
             (map ns-parser/get-libspecs-from-file)
             aliases-by-frequencies)
   :cljs (->> (util/filter-project-files util/cljs-file?)
              (map ns-parser/get-libspecs-from-file)
              aliases-by-frequencies)})
