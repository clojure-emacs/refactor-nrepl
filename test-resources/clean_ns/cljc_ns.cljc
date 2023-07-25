(ns clean-ns.cljc-ns
  (:require [vlad.core :as vlad :refer [attr chain join present Validation]]
            #?(:cljs [goog.date.Interval :as interval])
            [clojure.string :as string]
            [cemerick.url :as url]))


(defn interval
  [t]
  #?(:cljs (interval/Interval. t)))

(defrecord FakeRecord [selector validation]
  Validation
  (validate [{:keys [selector validation]} data]
    (string/join " " [selector validation data])))

(def join-usage
  (join
   (attr :name (present))
   (attr :age (present))))

(def chain-usage
  (attr :password
        (chain
         (present))))

(defn ->url
  [u]
  (url/url u))
