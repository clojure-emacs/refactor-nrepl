(ns com.move.dependent-ns1
  (:require [com.move.ns-to-be-moved
             :refer [fn-to-be-moved macro-to-be-moved var-to-be-moved]])
  (:import [com.move.ns_to_be_moved
            TypeToBeMoved RecordToBeMoved]))

(defn- use-some-publics []
  (macro-to-be-moved
   (fn-to-be-moved (TypeToBeMoved. :ok))
   (fn-to-be-moved (RecordToBeMoved. :ok))))
