(ns refactor-nrepl.ns.resolve-missing-test
  (:require
   [cider.piggieback :as piggieback]
   [clojure.edn :as edn]
   [clojure.java.shell :as shell]
   [clojure.test :refer [deftest is compose-fixtures testing use-fixtures]]
   [nrepl.core :as nrepl]
   [nrepl.server :as server]
   [refactor-nrepl.middleware :as middleware]))

(def ^:dynamic *handler* (server/default-handler #'middleware/wrap-refactor))
(def ^:dynamic *session* nil)

(defn session-fixture
  [f]
  (with-open [^nrepl.server.Server
              server (server/start-server :bind "localhost" :handler *handler*)
              ^nrepl.transport.FnTransport
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
  (compose-fixtures
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

(use-fixtures :each piggieback-fixture)

(deftest sanity
  (let [{:keys [exit]
         :as v} (shell/sh "node" "--version")]
    (assert (-> exit long zero?)
            (pr-str v)))

  (testing "cljs repl is active"
    (let [response (message {:op :eval
                             :code (nrepl/code js/console)})]
      (testing (pr-str response)
        (is (= "cljs.user" (:ns response)))
        (is (= #{"done"} (:status response))))))

  (testing "eval works"
    (let [response (message {:op :eval
                             :code (nrepl/code (map even? (range 6)))})]
      (testing (pr-str response)
        (is (= "cljs.user" (:ns response)))
        (is (= ["(true false true false true false)"] (:value response)))
        (is (= #{"done"} (:status response))))))

  (testing "errors handled properly"
    (let [response (message {:op :eval
                             :code (nrepl/code (ffirst 1))})]
      (testing (pr-str response)
        (is (= "class clojure.lang.ExceptionInfo"
               (:ex response)
               (:root-ex response)))
        (is (string? (:err response)))
        (is (= #{"eval-error" "done"} (:status response)))))))

(deftest resolve-missing-test
  (testing "Finds functions is regular namespaces"
    (let [{:keys [^String error] :as response} (message {:op :resolve-missing :symbol 'print-doc})
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
      (let [{:keys [^String error] :as response} (message {:op :resolve-missing :symbol 'dir})
            {:keys [name type]} (first (edn/read-string (:candidates response)))]
        (when error
          (throw (RuntimeException. error)))
        (testing (pr-str response)
          (is (= 'cljs.repl name))
          (is (= :macro type)))))))
