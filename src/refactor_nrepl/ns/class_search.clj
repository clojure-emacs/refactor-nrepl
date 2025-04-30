(ns refactor-nrepl.ns.class-search
  "Search the classpath for classes.

  Formerly known as `refactor-nrepl.ns.slam.hound.search`."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [compliment.utils])
  (:import
   (java.io File)))

(defn- get-available-classes []
  (let [classes (compliment.utils/classes-on-classpath)]
    (into []
          (comp (keep (fn [s]
                        ;; https://github.com/alexander-yakushev/compliment/issues/105
                        (when (io/resource (-> s (string/replace "." File/separator) (str ".class")))
                          s)))
                (distinct)
                (map symbol))
          classes)))

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
