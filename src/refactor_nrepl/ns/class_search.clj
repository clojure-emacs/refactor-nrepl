(ns refactor-nrepl.ns.class-search
  "Search the classpath for classes.

  Formerly known as `refactor-nrepl.ns.slam.hound.search`."
  (:require
   [clojure.string :as string]
   [compliment.utils]))

(defn- get-available-classes []
  (->> (dissoc (compliment.utils/classes-on-classpath)
               "")
       (vals)
       (reduce into)
       (mapv symbol)))

(def available-classes
  (delay (get-available-classes)))

(defn- get-available-classes-by-last-segment []
  (group-by #(symbol (peek (string/split (str %) #"\."))) @available-classes))

(def available-classes-by-last-segment
  (delay (get-available-classes-by-last-segment)))

(defn reset
  "Reset the cache of classes"
  []
  (alter-var-root #'available-classes (constantly (delay (get-available-classes))))
  (alter-var-root #'available-classes-by-last-segment (constantly (delay (get-available-classes-by-last-segment)))))
