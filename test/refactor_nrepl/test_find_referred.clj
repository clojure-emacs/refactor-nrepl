(ns refactor-nrepl.test-find-referred
  (:require [refactor-nrepl.test-util :as u]
            [refactor-nrepl.refactor :refer :all]
            [clojure.test :refer :all]))

(def test-ns-string
"(ns secret-santa.core
  (:require [clojure.set :refer [difference]]
            [clojure.string :refer [trim]]))

(defn- rnd-pair-up [participants]
  (let [receivers (shuffle participants)]
    (partition 2 (interleave participants receivers))))

(defn pair-up [participants]
  (trim \" fooobar \")
  (let [pairs (rnd-pair-up participants)]
    (if (some (fn [p] (= (first p) (second p))) pairs)
        (pair-up participants)
        pairs)))")

(def find-referred #'refactor-nrepl.refactor/find-referred)

(def test-ast (u/test-ast test-ns-string))

(deftest referred-found
  (is (find-referred test-ast "trim")))

(deftest referred-not-found
  (is (not (find-referred test-ast "difference"))))
