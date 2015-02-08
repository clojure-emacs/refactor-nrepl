(ns refactor-nrepl.ns.constructor
  (:require [clojure.string :as str]
            [refactor-nrepl.ns.helpers
             :refer [get-ns-component prefix-form? prefix suffix
                     index-of-component]]))

(defn- assert-single-alias
  [libspecs alias]
  (for [libspec libspecs
        :let [libspec-alias (:as libspec)]]
    (when (and libspec-alias (not= libspec-alias alias))
      (throw
       (IllegalStateException.
        (str (:ns libspec) " is aliased as both " alias
             " and " libspec-alias)))))
  alias)

(defn- get-libspec-alias [libspecs]
  (->> libspecs
       (map :as)
       (filter (complement nil?))
       first
       (assert-single-alias libspecs)))

(defn- get-referred-symbols [libspecs]
  (let [referred (->> libspecs
                      (map :refer)
                      (remove nil?))]
    (if (some #{:all} referred)
      :all
      (when (seq referred)
        (-> referred concat flatten distinct)))))

(defn- get-flags [libspecs]
  (let [flags  (->> libspecs
                    (mapcat :flags)
                    distinct)]
    (if (= (count flags) 3) [:reload-all :verbose] flags)))

(defn- merge-libspecs
  [libspecs]
  {:ns (:ns (first libspecs))
   :as (get-libspec-alias libspecs)
   :refer (get-referred-symbols libspecs)
   :rename (apply merge (map :rename libspecs))
   :flags (get-flags libspecs)})

(defn- remove-duplicate-libspecs
  [deps]
  (let [libspecs-by-ns (atom {})]
    (doseq [libspec deps]
      (swap! libspecs-by-ns update-in [(:ns libspec)] conj libspec))
    (for [libspecs (vals @libspecs-by-ns)]
      (merge-libspecs libspecs))))

(defn- get-sort-name [dep]
  (if (sequential? dep)
    (let [name (-> dep first name)
          ;; penalize prefix forms so [foo.bar :as bar]
          ;; comes before [foo.bar [qux :as qux] [quux ..]]
          suffix (if (and (> (count dep) 1) (sequential? (second dep)))
                   " ["
                   "")]
      (str name suffix))
    (name dep)))

(defn- dependency-comparator
  "Lexicographical comparison of dependency vectors based on the name
  of the included lib.

  Handles dependencies on the form 'name' as well as '[name ..]'"
  [d1 d2]
  (.compareTo (get-sort-name d1) (get-sort-name d2)))

(defn- sort-libspecs-with-prefix
  [libspec]
  (if (prefix-form? libspec)
    (apply vector (first libspec)
           (sort dependency-comparator (rest libspec)))
    libspec))

(defn- sort-libspecs
  "Each libspec is either [some.ns ..] or [some.ns foo bar ..] or
  [some.ns [foo ..] [bar ..]]"
  [libspecs]
  (->> libspecs
       (map sort-libspecs-with-prefix)
       (sort dependency-comparator)))

(defn- sort-referred-symbols [referred]
  (sort dependency-comparator referred))

(defn- ns-prefix
  "Extracts the prefix from a libspec."
  [{:keys [ns]}]
  (symbol (prefix ns)))

(defn- ns-suffix
  [{:keys [ns]}]
  (-> ns suffix symbol))

(defn- by-prefix
  [libspecs]
  (let [libspecs-by-prefix (atom {})]
    (doseq [libspec libspecs
            :let [prefix (ns-prefix libspec)]]
      (swap! libspecs-by-prefix update-in [prefix] conj libspec))
    @libspecs-by-prefix))

(defn- create-libspec
  [{:keys [ns as refer rename flags] :as libspec}]
  (if (and (not as) (not refer) (empty? flags) (empty? rename))
    ns
    (into [ns]
          (concat (when as [:as as])
                  (when refer
                    [:refer (if (sequential? refer)
                              (vec (sort-referred-symbols refer))
                              refer)])
                  (when rename [:rename (:rename libspec)])
                  flags))))

(defn- create-libspec-vectors-without-prefix
  [libspecs]
  (vec
   (for [libspec libspecs]
         (create-libspec (update-in libspec [:ns] #(-> % suffix symbol))))))

(defn- create-libspec-vectors-with-prefix
  [libspecs]
  (vec
   (for [libspec libspecs]
         (create-libspec libspec))))

(defn- create-prefixed-libspec-vector
  [libspecs]
  (vec
   (for [{:keys [ns] :as libspec} libspecs]
         (create-libspec (assoc libspec :ns (ns-suffix ns))))))

(defn- create-prefixed-libspec-vectors
  [[libspec & more :as libspecs]]
  (if-not more
    (create-libspec-vectors-with-prefix [libspec])
    [(into [(ns-prefix (first libspecs))]
           (create-libspec-vectors-without-prefix libspecs))]))

(defn- create-libspec-vectors
  [libspecs-by-prefix]
  (apply concat (for [[prefix libspecs] libspecs-by-prefix]
                  (if (= prefix :none)
                    (create-libspec-vectors-without-prefix libspecs)
                    (create-prefixed-libspec-vectors libspecs)))))

(defn- build-require-form
  [libspecs]
  (let [libspecs (->> libspecs
                      remove-duplicate-libspecs
                      by-prefix
                      create-libspec-vectors
                      sort-libspecs)]
    (when (seq libspecs)
      (cons :require libspecs))))

(defn- classes-by-prefix
  [classes]
  (let [by-prefix (atom {})]
    (doseq [class classes
            :let [package (prefix class)
                  class-name (suffix class)]]
      (swap! by-prefix update-in [package] conj class-name))
    @by-prefix))

(defn- create-import-form
  [prefix classes]
  (if (= (count classes) 1)
    (symbol (str prefix "." (first classes)))
    (into [(symbol prefix)] (map symbol classes))))

(defn- create-import-components
  [classes-by-prefix]
  (for [[prefix classes] classes-by-prefix]
    (create-import-form prefix classes)))

(defn- sort-imports
  [imports]
  (->> imports
       (map #(if (sequential? %)
               (vec (cons (first %)
                          (sort dependency-comparator (rest %))))
               %))
       (sort dependency-comparator)))

(defn- build-import-form
  [classes]
  (let [import-form (->> classes
                         classes-by-prefix
                         create-import-components
                         sort-imports)]
    (when (seq import-form)
      (cons :import import-form))))

(defn- drop-index [col idx]
  (filter identity (map-indexed #(if (not= %1 idx) %2) col)))

(defn- remove-use-clause
  [ns-form]
  (if-let [idx (index-of-component ns-form :use)]
    (drop-index ns-form idx)
    ns-form))

(defn- update-require-clause
  [ns-form new-require-form]
  (if-let [idx (and new-require-form (or (index-of-component ns-form :require)
                                         (index-of-component ns-form :use)
                                         (count ns-form)))]
    (apply list (assoc (vec ns-form) idx new-require-form))
    (drop-index ns-form (index-of-component ns-form :require))))

(defn- update-import-clause
  [ns-form new-import-form]
  (if-let [idx (and new-import-form (index-of-component ns-form :import))]
    (apply list (assoc (vec ns-form) idx new-import-form))
    (drop-index ns-form (index-of-component ns-form :import))))

(defn rebuild-ns-form
  [ns-form deps]
  (let [new-require-form (build-require-form (:require deps))
        new-import-form (build-import-form (:import deps))]
    (-> ns-form
        (update-require-clause new-require-form)
        (update-import-clause new-import-form)
        remove-use-clause)))
