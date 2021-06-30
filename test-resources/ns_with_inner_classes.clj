(ns ns-with-inner-classes
  (:import [java.awt.geom Line2D$Double]))

(defn foo []
  (Line2D$Double. 0 0 5 5))
