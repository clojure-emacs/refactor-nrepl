(ns refactor-nrepl.ns.resolve-missing-test
  (:require
   [cider.piggieback :as piggieback]
   [clojure.edn :as edn]
   [clojure.java.shell :as shell]
   [clojure.test :refer [deftest is compose-fixtures testing use-fixtures]]
   [nrepl.core :as nrepl]
   [nrepl.server :as server]
   [refactor-nrepl.middleware :as middleware]
   [refactor-nrepl.test-session :as session]))

(def piggieback-fixture
  (compose-fixtures
   session/session-fixture
   (fn [f]
     (binding [session/*handler* (server/default-handler
                                  #'middleware/wrap-refactor
                                  #'piggieback/wrap-cljs-repl)]
       (session/message {:op :eval
                         :code (nrepl/code
                                (require '[cider.piggieback :as piggieback])
                                (require '[cljs.repl.node :as node])
                                (piggieback/cljs-repl (node/repl-env)))})
       (f)
       (session/message {:op :eval
                         :code (nrepl/code :cljs/quit)})))))

(use-fixtures :each piggieback-fixture)

(deftest sanity
  (let [{:keys [exit]
         :as v} (shell/sh "node" "--version")]
    (assert (-> exit long zero?)
            (pr-str v)))

  (testing "cljs repl is active"
    (let [response (session/message {:op :eval
                                     :code (nrepl/code js/console)})]
      (testing (pr-str response)
        (is (= "cljs.user" (:ns response)))
        (is (= #{"done"} (:status response))))))

  (testing "eval works"
    (let [response (session/message {:op :eval
                                     :code (nrepl/code (map even? (range 6)))})]
      (testing (pr-str response)
        (is (= "cljs.user" (:ns response)))
        (is (= ["(true false true false true false)"] (:value response)))
        (is (= #{"done"} (:status response))))))

  (testing "errors handled properly"
    (let [response (session/message {:op :eval
                                     :code (nrepl/code (ffirst 1))})]
      (testing (pr-str response)
        (is (= "class clojure.lang.ExceptionInfo"
               (:ex response)
               (:root-ex response)))
        (is (string? (:err response)))
        (is (= #{"eval-error" "done"} (:status response)))))))

(deftest resolve-missing-test
  (testing "Finds functions is regular namespaces"
    (let [{:keys [^String error] :as response} (session/message {:op :resolve-missing :symbol 'print-doc})
          _ (assert (string? (:candidates response))
                    (pr-str response))
          {:keys [name type]} (first (edn/read-string (:candidates response)))]
      (when error
        (println error)
        (throw (RuntimeException. error)))
      (testing (pr-str response)
        (is (= 'cljs.repl name))
        (is (= :ns type))))
    (testing "Finds macros"
      (let [{:keys [^String error] :as response} (session/message {:op :resolve-missing :symbol 'dir})
            {:keys [name type]} (first (edn/read-string (:candidates response)))]
        (when error
          (throw (RuntimeException. error)))
        (testing (pr-str response)
          (is (= 'cljs.repl name))
          (is (= :macro type)))))))
