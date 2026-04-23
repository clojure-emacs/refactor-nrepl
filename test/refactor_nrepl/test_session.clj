(ns refactor-nrepl.test-session
  "Shared nREPL client session helpers for integration-style tests.

  Modeled on `cider.nrepl.test-session`. Tests that need an in-process nREPL
  server with refactor-nrepl's middleware installed can just do:

      (use-fixtures :each refactor-nrepl.test-session/session-fixture)

  and then call `(message {:op \"...\" ...})` to round-trip requests."
  (:require
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
