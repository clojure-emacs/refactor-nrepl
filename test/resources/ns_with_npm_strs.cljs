(ns resources.ns-with-npm-strs
  (:require ["react-native" :as rn]
            [clojure.string :as str]
            ["@react-native-community/async-storage" :as async-storage]
            ["@fortawesome/react-native-fontawesome" :as fa]
            ["@fortawesome/free-solid-svg-icons" :as fa-icons]))

(defmacro black-hole [& body])

(black-hole
 rn/View
 str/join
 fa/FontAwesomeIcon
 fa-icons/faCoffee)
