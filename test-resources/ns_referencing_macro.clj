(ns ns-referencing-macro
  (:require [ns1 :refer [black-hole]]))

(black-hole 'foo 'bar)
