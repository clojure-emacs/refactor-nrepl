(ns refactor-nrepl.util.meta
  "Metadata-oriented helpers."
  (:refer-clojure :exclude [distinct]))

(defn distinct
  "Like `#'clojure.core/distinct`, but takes a 2-arg `f` that will choose/build the winning value whenever to equal ones are found.

  This helps merging metadata according to custom rules."
  [f coll]
  (let [index (volatile! {})]
    (reduce (fn [acc x]
              (let [entry (find @index x)
                    i (some-> entry val)
                    acc (cond-> acc
                          entry (update i (fn [existing-value]
                                            (f existing-value x)))
                          (not entry) (conj x))]
                (when-not entry
                  (vswap! index assoc x (dec (count acc))))
                acc))
            []
            coll)))
