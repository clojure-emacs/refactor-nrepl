(ns refactor-nrepl.test-session
  "Shared nREPL client session helpers for integration-style tests.

  Modeled on `cider.nrepl.test-session`. Tests that need an in-process nREPL
  server with refactor-nrepl's middleware installed can just do:

      (use-fixtures :each refactor-nrepl.test-session/session-fixture)

  and then call `(message {:op \"...\" ...})` to round-trip requests."
  (:require
   [clojure.test :refer [is]]
   [nrepl.core :as nrepl]
   [nrepl.middleware :refer [set-descriptor!]]
   [nrepl.server :as server]
   [nrepl.transport :as transport]
   [refactor-nrepl.descriptors :as descriptors]
   [refactor-nrepl.middleware :as middleware]))

;;; Holding the ops to their descriptors
;;
;; Every response an op sends during the tests is checked against the
;; `:returns` of its descriptor, so that a new response key can't go
;; undocumented. Modeled on cider-nrepl's descriptor contract.

(def ^:private standard-keys
  "The response keys that aren't specific to an op: nREPL's own, and the ones
  errors are reported under."
  #{:id :session :status :error :err :ex})

(def undocumented-keys
  "The response keys seen so far that the descriptor of the op that sent
  them doesn't list, by op."
  (atom {}))

(defn- note-undocumented-keys! [op response]
  (let [bare-name (middleware/bare-op-name op)
        documented (set (map keyword (keys (get-in descriptors/ops [bare-name :returns]))))]
    (when-let [undocumented (seq (remove (some-fn standard-keys documented) (keys response)))]
      (swap! undocumented-keys update bare-name (fnil into #{}) undocumented))))

(defn- checking-transport [op transport]
  (reify transport/Transport
    (recv [_] (transport/recv transport))
    (recv [_ timeout] (transport/recv transport timeout))
    (send [this response]
      (note-undocumented-keys! op response)
      (transport/send transport response)
      this)))

(defn wrap-refactor-checked
  "`middleware/wrap-refactor`, with the responses of its ops checked against
  their descriptors."
  [handler]
  (let [refactor-handler (middleware/wrap-refactor handler)]
    (fn [{:keys [op] :as msg}]
      (refactor-handler (cond-> msg
                          (contains? @middleware/refactor-nrepl-ops op)
                          (update :transport #(checking-transport op %)))))))

(set-descriptor! #'wrap-refactor-checked
                 (:nrepl.middleware/descriptor (meta #'middleware/wrap-refactor)))

(def ^:dynamic *handler* (server/default-handler #'wrap-refactor-checked))
(def ^:dynamic *session* nil)
(def ^:dynamic *transport*
  "The transport `*session*` talks through, for the tests that set up their
  own clients."
  nil)

(defn session-fixture
  [f]
  (with-open [^nrepl.server.Server
              server (server/start-server :bind "localhost" :handler *handler*)
              ^nrepl.transport.FnTransport
              transport (nrepl/connect :port (:port server))]
    (let [client (nrepl/client transport Long/MAX_VALUE)]
      (try
        (binding [*session* (nrepl/client-session client)
                  *transport* transport]
          (f))
        (finally
          ;; Also when a test blows up, so that what it left behind isn't
          ;; pinned on the next namespace to use the fixture.
          (let [undocumented (first (reset-vals! undocumented-keys {}))]
            (is (empty? undocumented)
                "ops replied with keys that are missing from the :returns of their descriptors")))))))

(defn message
  ([msg] (message msg true))
  ([msg combine-responses?]
   (let [responses (nrepl/message *session* msg)]
     (if combine-responses?
       (nrepl/combine-responses responses)
       responses))))
