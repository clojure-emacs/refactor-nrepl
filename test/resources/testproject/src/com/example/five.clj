(ns com.example.five
  (:require [clojure.string :refer [join split blank? trim] :as str]
            [refactor-nrepl.core :as core]))

;;  remove parameters to run the tests
(defn fn-with-unbounds [s sep]
  (when-not (blank? s)
    (-> s (split #" ")
        (join sep)
        trim)))

(defn orig-fn [s]
  (let [sep ";"]
    (when-not (blank? s)
      (-> s
          (split #" ")
          ((partial join sep))
          trim))))

(defn find-in-let [s p]
  (let [z (trim p)]
    (assoc {:s s
            :p p
            :z z} :k "foobar")))

(defn threading-macro [strings]
  (let [sep ","]
    (->> strings
         flatten
         (join sep))))

(defn repeated-sexp []
  (map name [:a :b :c])
  (let [name #(str "myname" %)]
    (map name [:a :b :c])))

(defn sexp-with-anon-fn [n]
  (let [g 5]
    (#(+ g %) n)))

(defn many-params [x y z a b c]
  (* x y z a b c))

(defn fn-with-default-optmap
  [{:keys [foo bar] :or {foo "foo"}}]
  [:bar :foo]
  (count foo))

(defn fn-with-default-optmap-linebreak
  [{:keys [foo
           bar]
    :or {foo
         "foo"}}]
  [:bar :foo]
  (count foo))

(defn fn-with-let-default-optmap []
  (let [{:keys [foo bar] :or {foo "foo"}} (hash-map)]
    [:bar :foo]
    (count foo)))

(core/with-clojure-version->= {:major 1 :minor 9}
  (defn fn-with-let-with-namespaced-keyword-destructuring []
    ;; https://github.com/clojure-emacs/refactor-nrepl/issues/289
    (let [{::str/keys [foo bar]} (hash-map)]
      [:bar :foo]
      (count foo))))

;; This was causing both find-local-symbol and find-macros to blow up, for
;; different reasons
::str/bar
