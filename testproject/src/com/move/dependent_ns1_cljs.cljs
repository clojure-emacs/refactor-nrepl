(ns com.move.dependent-ns1-cljs
  (:require [com.move.ns-to-be-moved-cljs
             :refer [fn-to-be-moved var-to-be-moved TypeToBeMoved RecordToBeMoved]])
  (:require-macros [com.move.ns-to-be-moved :refer [macro-to-be-moved]]))

(defn- use-some-publics []
  (macro-to-be-moved
   (fn-to-be-moved (TypeToBeMoved. :ok))
   (fn-to-be-moved (RecordToBeMoved. :ok))))
