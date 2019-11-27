(ns refactor-nrepl.ns.rebuild
  (:require [clojure.string :as str]
            [refactor-nrepl
             [config :as config]
             [util :as util]]
            [refactor-nrepl.core :refer [index-of-component prefix prefix-form? suffix]]))

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

(defn- merge-referred-symbols [libspecs key]
  (let [referred (->> libspecs
                      (map key)
                      (remove nil?))]
    (if (some #{:all} referred)
      :all
      (when (seq referred)
        (-> referred concat flatten distinct)))))

(defn- remove-redundant-flags
  [{:keys [reload reload-all] :as libspec}]
  (if (and reload reload-all)
    (dissoc libspec :reload)
    libspec))

(defn- merge-libspecs
  [libspecs]
  (->
   (apply merge libspecs)
   (merge {:as (get-libspec-alias libspecs)
           :refer (merge-referred-symbols libspecs :refer)
           :refer-macros (merge-referred-symbols libspecs :refer-macros)
           :rename (apply merge (map :rename libspecs))})
   remove-redundant-flags))

(defn- remove-duplicate-libspecs
  [deps]
  (let [libspecs-by-ns (atom {})]
    (doseq [libspec deps]
      (swap! libspecs-by-ns update-in [(:ns libspec)] conj libspec))
    (for [libspecs (vals @libspecs-by-ns)]
      (merge-libspecs libspecs))))

(defn- get-sort-name
  ^String [dep]
  (str/lower-case
   (if (sequential? dep)
     (let [name (-> dep first pr-str)
           ;; penalize prefix forms so [foo.bar :as bar]
           ;; comes before [foo.bar [qux :as qux] [quux ..]]
           suffix (if (and (> (count dep) 1) (sequential? (second dep)))
                    " ["
                    "")]
       (str name suffix))
     (name dep))))

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
  (if (= referred :all)
    :all
    (sort dependency-comparator referred)))

(defn- type-preserving [f x]
  (when x
    (when-let [r (f x)]
      (cond
        (string? x) (str r)
        (symbol? x) (symbol r)))))

(defn- ns-prefix
  "Extracts the prefix from a libspec."
  [{:keys [ns]}]
  (or (type-preserving prefix ns)
      :none))

(defn- ns-suffix
  [{:keys [ns]}]
  (type-preserving suffix ns))

(defn- by-prefix
  [libspecs]
  (let [libspecs-by-prefix (atom {})]
    (doseq [libspec libspecs
            :let [prefix (ns-prefix libspec)]]
      (swap! libspecs-by-prefix update-in [prefix] conj libspec))
    @libspecs-by-prefix))

(defn- create-libspec
  [{:keys [ns as refer rename refer-macros] :as libspec}]
  (let [all-flags #{:reload :reload-all :verbose :include-macros}
        flags (util/filter-map #(all-flags (first %)) libspec)]
    (if (and (not as) (not refer)
             (empty? flags) (empty? rename) (empty? refer-macros))
      ns
      (into [ns]
            (concat (when as [:as as])
                    (when refer
                      [:refer (if (sequential? refer)
                                (vec (sort-referred-symbols refer))
                                refer)])
                    (when refer-macros
                      [:refer-macros (vec (sort-referred-symbols refer-macros))])
                    (when (not-empty rename)
                      [:rename (into (sorted-map) (:rename libspec))])
                    (flatten (seq flags)))))))

(defn- create-libspec-vectors-without-prefix
  [libspecs]
  (vec
   (for [libspec libspecs]
     (create-libspec (assoc libspec :ns (ns-suffix libspec))))))

(defn- create-libspec-vectors-with-prefix
  [libspecs]
  (vec
   (for [libspec libspecs]
     (create-libspec libspec))))

(defn- create-prefixed-libspec-vectors
  [[libspec & more :as libspecs]]
  (if-not (:prefix-rewriting config/*config*)
    (create-libspec-vectors-with-prefix libspecs)
    (if-not more
      (create-libspec-vectors-with-prefix [libspec])
      [(into [(ns-prefix (first libspecs))]
             (create-libspec-vectors-without-prefix libspecs))])))

(defn- create-libspec-vectors
  [libspecs-by-prefix]
  (apply concat (for [[prefix libspecs] libspecs-by-prefix]
                  (if (= prefix :none)
                    (create-libspec-vectors-without-prefix libspecs)
                    (create-prefixed-libspec-vectors libspecs)))))

(defn- build-require-form
  [libspecs]
  (let [libspecs (-> libspecs
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
      (swap! by-prefix update-in [package] (comp distinct conj) class-name))
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

(defn- build-require-macros-form [libspecs]
  (when-let [require-form (build-require-form libspecs)]
    (cons :require-macros (rest require-form))))

(defn- drop-dependency-clauses
  "Drop every form from the ns-form which related to bringing symbols
  into the namespace."
  [ns-form]
  (remove (fn [component]
            (#{:require :require-macros :use :use-macros :import}
             (and (sequential? component) (first component))))
          ns-form))

(defn build-clj-or-cljs-dep-forms [deps dialect]
  (let [deps (dialect deps)
        forms (remove nil?
                      (list
                       (build-require-form (:require deps))
                       (when (= dialect :cljs)
                         (build-require-macros-form (:require-macros deps)))
                       (build-import-form (:import deps))))]
    (when (seq forms)
      forms)))

(defn build-cljc-dep-forms [deps]
  (let [rdr-cond (symbol "#?@")
        clj-forms (build-clj-or-cljs-dep-forms deps :clj)
        cljs-forms (build-clj-or-cljs-dep-forms deps :cljs)]
    (if (and clj-forms
             cljs-forms
             (not= clj-forms cljs-forms))
      (list rdr-cond `(:clj ~(vec clj-forms) :cljs ~(vec cljs-forms)))
      (or clj-forms cljs-forms))))

(defn build-dep-forms
  [{:keys [source-dialect] :as deps}]
  (if (= source-dialect :cljc)
    (build-cljc-dep-forms deps)
    (build-clj-or-cljs-dep-forms deps source-dialect)))

(defn rebuild-ns-form
  [deps old-ns-form]
  (with-meta
    (-> old-ns-form
        drop-dependency-clauses
        reverse
        (into (build-dep-forms deps))
        reverse)
    (meta old-ns-form)))
