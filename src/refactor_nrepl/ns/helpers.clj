(ns refactor-nrepl.ns.helpers)

(defn- libspec?
  [thing]
  (or (vector? thing)
      (symbol? thing)))

(defn prefix-form?
  "True if the vector is of the form [prefix libspec1 libspec2...]"
  [v]
  (and (sequential? v)
       (symbol? (first v))
       (every? libspec? (rest v))))

(defn index-of-component [ns-form type]
  (->> (keep-indexed #(when (and (sequential? %2) (= (first %2) type)) %1)
                     ns-form)
       first))

(defn get-ns-component
  [ns type]
  "Extracts a sub-component from the ns declaration.

type is either :require, :use or :import"
  (some->> (index-of-component ns type) (nth ns)))

(defn get-ns-components
  "Returns a map of with keys :require, :use and :import"
  [ns]
  (let [components [:require :use :import]]
    (->> components
         (map (partial get-ns-component ns))
         (zipmap components)
         merge)))
