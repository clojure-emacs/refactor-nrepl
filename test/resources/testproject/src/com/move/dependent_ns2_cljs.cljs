(ns com.move.dependent-ns2-cljs
  (:require [com.move.ns-to-be-moved-cljs :refer [fn-to-be-moved]]))

(defn- use-some-private-stuff []
  (#'com.move.ns-to-be-moved/private-fn-to-be-moved
   com.move.ns_to_be_moved.RecordToBeMovedAndFullyQualified)
  (#'com.move.ns-to-be-moved/private-fn-to-be-moved
   com.move.ns_to_be_moved.TypeToBeMovedAndFullyQualified)
  (fn-to-be-moved #'com.move.ns-to-be-moved/private-var-to-be-moved))
