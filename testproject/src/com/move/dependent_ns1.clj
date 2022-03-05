(ns com.move.dependent-ns1
  (:require [com.move.ns-to-be-moved
             :refer [fn-to-be-moved macro-to-be-moved var-to-be-moved]])
  (:import [com.move.ns_to_be_moved
            TypeToBeMoved RecordToBeMoved]))

(defn- use-some-publics []

  (com.move.ns-to-be-moved/fn-to-be-moved :_)

  'com.move.ns-to-be-moved/fn-to-be-moved

  #'com.move.ns-to-be-moved/fn-to-be-moved

  ^:com.move.ns-to-be-moved/fn-to-be-moved []

  #:com.move.ns-to-be-moved {:a :b}

  (let [^com.move.ns_to_be_moved.TypeToBeMoved x (fn-to-be-moved :_)]
    x)

  ;; https://github.com/clojure-emacs/refactor-nrepl/issues/172
  (partial instance? com.move.ns_to_be_moved.TypeToBeMoved)

  (com.move.ns_to_be_moved.TypeToBeMoved. :ok)

  (macro-to-be-moved
   (fn-to-be-moved (TypeToBeMoved. :ok))
   (fn-to-be-moved (RecordToBeMoved. :ok))))
