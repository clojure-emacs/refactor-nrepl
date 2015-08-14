(ns resources.cljsns
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<!]]
            [undead.components :refer [render-game]]
            [clojure.pprint :as pprint :require-macros true]
            [clojure.set]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn use-some-of-it
  (go (<! (ws-ch)))
  (render-game 'game))

(defmacro a-macro []
  (pprint/pprint "string"))
