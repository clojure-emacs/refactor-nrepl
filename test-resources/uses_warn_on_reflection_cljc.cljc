(ns uses-warn-on-reflection-cljc)

#?(:clj (set! *warn-on-reflection* true))

(defn echo [x]
  x)
