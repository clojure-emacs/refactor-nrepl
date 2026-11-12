(ns throws-at-load)

(def state nil)

(defn parse-operation [x]
  (inc x))

(def answer (parse-operation @state))
