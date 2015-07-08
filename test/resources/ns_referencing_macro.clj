(ns resources.ns-referencing-macro
  (:require [resources.ns1 :refer [black-hole]]))

(black-hole 'foo 'bar)
