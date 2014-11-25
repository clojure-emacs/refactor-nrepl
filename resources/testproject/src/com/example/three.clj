(ns com.example.three)

(defn fn-with-println [a]
  (println "a: " a)
  (if a
    (str a)
    a))
