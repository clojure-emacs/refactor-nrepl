(ns refactor-nrepl.ns.resolve-missing-test
  (:require [cider.piggieback :as piggieback]
            [clojure
             [edn :as edn]
             [test :as t]]
            [nrepl.core :as nrepl]
            [nrepl.server :as server]
            [refactor-nrepl.middleware :as middleware]))

(def ^:dynamic *handler* (server/default-handler #'middleware/wrap-refactor))
(def ^:dynamic *session* nil)

(defn session-fixture
  [f]
  (with-open [server (server/start-server :bind "localhost" :handler *handler*)
              transport (nrepl/connect :port (:port server))]
    (let [client (nrepl/client transport Long/MAX_VALUE)]
      (binding [*session* (nrepl/client-session client)]
        (f)))))

(defn message
  ([msg] (message msg true))
  ([msg combine-responses?]
   (let [responses (nrepl/message *session* msg)]
     (if combine-responses?
       (nrepl/combine-responses responses)
       responses))))

(def piggieback-fixture
  (t/compose-fixtures
   session-fixture
   (fn [f]
     (binding [*handler* (server/default-handler
                          #'refactor-nrepl.middleware/wrap-refactor
                          #'piggieback/wrap-cljs-repl)]
       (message {:op :eval
                 :code (nrepl/code
                        (require '[cider.piggieback :as piggieback])
                        (require '[cljs.repl.node :as node])
                        (piggieback/cljs-repl (node/repl-env)))})
       (f)
       (message {:op :eval
                 :code (nrepl/code :cljs/quit)})))))

(t/use-fixtures :each piggieback-fixture)

(t/deftest sanity
  (t/testing "cljs repl is active"
    (let [response (message {:op :eval
                             :code (nrepl/code js/console)})]
      (t/is (= "cljs.user" (:ns response)))
      (t/is (= #{"done"} (:status response)))))

  (t/testing "eval works"
    (let [response (message {:op :eval
                             :code (nrepl/code (map even? (range 6)))})]
      (t/is (= "cljs.user" (:ns response)))
      (t/is (= ["(true false true false true false)"] (:value response)))
      (t/is (= #{"done"} (:status response)))))

  (t/testing "errors handled properly"
    (let [response (message {:op :eval
                             :code (nrepl/code (ffirst 1))})]
      (t/is (= "class clojure.lang.ExceptionInfo"
               (:ex response)
               (:root-ex response)))
      (t/is (string? (:err response)))
      (t/is (= #{"eval-error" "done"} (:status response))))))

(t/deftest resolve-missing-test
  (t/testing "Finds functions is regular namespaces"
    (let [{:keys [^String error] :as response} (message {:op :resolve-missing :symbol 'print-doc})
          {:keys [name type]} (first (edn/read-string (:candidates response)))]
      (when error
        (println error)
        (throw (RuntimeException. error)))
      (t/is (= 'cljs.repl name))
      (t/is (= :ns type)))
    (t/testing "Finds macros"
      (let [{:keys [error] :as response} (message {:op :resolve-missing :symbol 'dir})
            {:keys [name type]} (first (edn/read-string (:candidates response)))]
        (when error
          (throw (RuntimeException. error)))
        (t/is (= 'cljs.repl name))
        (t/is (= :macro type))))))
