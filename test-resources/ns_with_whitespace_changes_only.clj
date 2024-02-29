(ns ns-with-whitespace-changes-only

  (:require   [clojure.string :as  str]
            [  clojure.walk   :as walk]))



(defn foo [x]
  (walk/postwalk (comp vector str/trim str) x))
