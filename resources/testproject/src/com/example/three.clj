(ns com.example.three)

(defn fn-with-println [a]
  (println "a: " a)
  (if a
    (str a)
    a))

(defn fn-with-let [left]
  (let [right (+ left 10)]
    (+ right left)))
