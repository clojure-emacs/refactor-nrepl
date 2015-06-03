;; Taken from https://github.com/alexander-yakushev/compliment 0.2.4
;; Copyright © 2013-2014 Alexander Yakushev. Distributed under the
;; Eclipse Public License, the same as Clojure.
(ns refactor-nrepl.find.bindings)

(def ^:private let-like-forms
  "Forms that create binding vector like let does."
  '#{let if-let when-let if-some when-some})

(def ^:private defn-like-forms
  "Forms that create binding vector like defn does."
  '#{defn defn- fn defmacro})

(def ^:private doseq-like-forms
  "Forms that create binding vector like doseq does."
  '#{doseq for})

(defn parse-binding
  "Given a binding node returns the list of local bindings introduced by that
  node. Handles vector and map destructuring."
  [binding-node]
  (cond (vector? binding-node)
        (mapcat parse-binding binding-node)

        (map? binding-node)
        (let [normal-binds (->> (keys binding-node)
                                (remove keyword?)
                                (mapcat parse-binding))
              keys-binds (if-let [ks (:keys binding-node)]
                           (mapv str ks) ())
              as-binds (if-let [as (:as binding-node)]
                         [(str as)] ())]
          (concat normal-binds keys-binds as-binds))

        (not (#{'& '_} binding-node))
        [(str binding-node)]))

(defn extract-local-bindings
  "When given a form that has a binding vector traverses that binding vector and
  returns the list of all local bindings."
  [form]
  (when (list? form)
    (cond (let-like-forms (first form))
          (mapcat parse-binding (take-nth 2 (second form)))

          (defn-like-forms (first form))
          (mapcat parse-binding
                  (loop [[c & r] (rest form), bnodes []]
                    (cond (nil? c) bnodes
                          (list? c) (recur r (conj bnodes (first c)))
                          (vector? c) c
                          :else (recur r bnodes))))

          (doseq-like-forms (first form))
          (->> (partition 2 (second form))
               (mapcat (fn [[left right]]
                         (if (= left :let)
                           (take-nth 2 right) [left])))
               (mapcat parse-binding))

          :else #{})))
