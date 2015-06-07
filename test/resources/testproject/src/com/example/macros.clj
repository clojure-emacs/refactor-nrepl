(ns com.example.macros)

(defn assert-nicely [x]
  (assert (> x 0))
  (+ x 10))

(defn str-nicely [x]
  (str "nice:" x))

(defmacro nicely [x]
  `(do
     (assert-nicely ~x)
     (str-nicely ~x)))

(defn use-nicely [x]
  (println "x:" x)
  (nicely x))
