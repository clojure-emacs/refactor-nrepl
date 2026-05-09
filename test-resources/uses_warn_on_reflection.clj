(ns uses-warn-on-reflection)

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn echo [x]
  x)
