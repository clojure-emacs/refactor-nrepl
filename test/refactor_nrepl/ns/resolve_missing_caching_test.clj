(ns refactor-nrepl.ns.resolve-missing-caching-test
  (:require [clojure
             [edn :as edn]]
            [nrepl.core :as nrepl]
            [refactor-nrepl.ns.resolve-missing-test :refer [session-fixture]]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as str]))

(use-fixtures :each session-fixture)

(defn message [arg-map]
  (let [{:keys [^String error] :as response}
        (refactor-nrepl.ns.resolve-missing-test/message arg-map)]
    (when error
      (throw (RuntimeException. error)))
    response))

(defn nrepl-intern-var [ns name]
  (message {:op :eval
            :ns ns
            :code (nrepl/code* `(defn ~name [] :sentinel))}))

(defn nrepl-deftype [ns name]
  (message {:op :eval
            :ns ns
            :code (nrepl/code* `(deftype ~name [~'a-field]))}))

(defn nrepl-resolve-missing [sym]
  (message {:op :resolve-missing :symbol sym}))

(deftest caching-test
  (testing "Finds newly defined symbols"
    (let [this-ns 'refactor-nrepl.ns.resolve-missing-caching-test
          varname (gensym 'new-function-)]

      (nrepl-resolve-missing varname)    ;; fill cache
      (nrepl-intern-var this-ns varname) ;; intern new var

      (let [response (nrepl-resolve-missing varname)]
        (is (seq (:candidates response)))

        (let [{:keys [name type]} (first (edn/read-string (:candidates response)))]
          (is (= :sentinel ((ns-resolve this-ns varname))))
          (is (= this-ns name))
          (is (= :ns type))))))

  (testing "Finds newly defined classes"
    (let [this-ns 'refactor-nrepl.ns.resolve-missing-caching-test
          varname (gensym 'NewType)]

      (nrepl-resolve-missing varname) ;; fill cache
      (nrepl-deftype this-ns varname) ;; intern new var

      (let [response (nrepl-resolve-missing varname)]
        (is (seq (:candidates response)))

        (let [{:keys [name type]} (first (edn/read-string (:candidates response)))]
          (is (= (symbol (str/replace (str this-ns "." varname) #"-" "_")) name))
          (is (= :type type)))))))
