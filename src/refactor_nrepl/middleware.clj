(ns refactor-nrepl.middleware
  (:require
   [cider.nrepl.middleware.util.cljs :as cljs]
   [clojure.stacktrace :refer [print-cause-trace]]
   [clojure.walk :as walk]
   [refactor-nrepl.config :as config]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.ns.libspec-allowlist :as libspec-allowlist]
   [refactor-nrepl.stubs-for-interface :refer [stubs-for-interface]]))

;; Compatibility with the legacy tools.nrepl.
;; It is not recommended to use the legacy tools.nrepl,
;; therefore it is guarded with a system property.
;; Specifically, we don't want to require it by chance.
(when-not (resolve 'set-descriptor!)
  (if (and (System/getProperty "refactor-nrepl.internal.try-requiring-tools-nrepl")
           (try
             (require 'clojure.tools.nrepl)
             true
             (catch Exception _
               false)))
    (require
     '[clojure.tools.nrepl.middleware :refer [set-descriptor!]]
     '[clojure.tools.nrepl.misc :refer [response-for]]
     '[clojure.tools.nrepl.transport :as transport])
    (require
     '[nrepl.middleware :refer [set-descriptor!]]
     '[nrepl.misc :refer [response-for]]
     '[nrepl.transport :as transport])))

(defn- require-and-resolve [sym]
  (locking core/require-lock
    (require (symbol (namespace sym))))
  (resolve sym))

(defn- err-info
  [ex status]
  {:ex (str (class ex))
   :err (with-out-str (print-cause-trace ex))
   :status #{status :done}})

(defn- reply-error
  "Build the error fragment of an nREPL response for `ex`.
  `ExceptionInfo`, `IllegalArgumentException`, and `IllegalStateException`
  are treated as user-facing errors (message only); anything else ships
  with a full stacktrace."
  [ex]
  (when (System/getProperty "refactor-nrepl.internal.pst")
    (.printStackTrace ^Throwable ex))
  (cond
    (instance? clojure.lang.ExceptionInfo ex)
    {:error (.toString ^Throwable ex) :status #{:done}}

    (or (instance? IllegalArgumentException ex)
        (instance? IllegalStateException ex))
    {:error (.getMessage ^Throwable ex) :status #{:done}}

    :else
    (err-info ex :refactor-nrepl-error)))

(defmacro ^:private with-errors-being-passed-on [transport msg & body]
  `(try
     ~@body
     (catch Throwable e#
       (transport/send ~transport (response-for ~msg (reply-error e#))))))

(defmacro ^:private reply [transport msg & kvs]
  `(libspec-allowlist/with-memoized-libspec-allowlist
     (with-errors-being-passed-on ~transport ~msg
       (config/with-config ~msg
         (transport/send ~transport
                         (response-for ~msg ~(apply hash-map kvs)))))))

(defn- bencode-friendly-data [data]
  ;; Bencode only supports byte strings, integers, lists and maps.
  ;; To prevent the bencode serializer in nrepl blowing up we manually
  ;; convert certain data types.
  ;; See refactor-nrepl#180 for more details.
  (walk/postwalk (fn [v]
                   (cond
                     (or (keyword? v) (symbol? v))
                     (if-let [prefix (core/prefix v)]
                       (core/fully-qualify prefix v)
                       (name v))

                     (set? v) (list v)

                     :else v))
                 data))

(defn- serialize-response [{:keys [serialization-format]} response]
  (binding [*print-length* nil
            *print-level* nil]
    (condp = serialization-format
      "edn" (pr-str response)
      "bencode" (bencode-friendly-data response)
      (pr-str response) ; edn as default
      )))

;;; Op registration
;;
;; Ops are registered in `refactor-nrepl-ops` via `def-op` (or by direct
;; assoc for ops with non-standard reply logic). `def-op` is shorthand
;; for the common case: a handler that takes the incoming msg and whose
;; return value gets echoed back under a single response key.

(defonce refactor-nrepl-ops (atom {}))

(defn- register-op! [op-name reply-fn]
  (swap! refactor-nrepl-ops assoc op-name reply-fn))

(defmacro def-op
  "Register a simple nREPL op.

  `op-name`      - op name (string).
  `handler-sym`  - quoted fully-qualified symbol of the handler fn;
                   required and resolved lazily on first use.
  `result-key`   - keyword under which to send the handler's return value.

  Options:
    :serialize? - pass the handler result through `serialize-response`
                  before replying (honors :serialization-format in msg)
    :pr-str     - pr-str the handler result before replying
    :no-args?   - invoke the handler with no arguments (default: pass msg)"
  [op-name handler-sym result-key & {:keys [serialize? pr-str no-args?]}]
  (let [handler (gensym "handler-")
        msg (gensym "msg-")
        result (if no-args?
                 `(@~handler)
                 `(@~handler ~msg))
        value (cond
                serialize? `(serialize-response ~msg ~result)
                pr-str     `(pr-str ~result)
                :else      result)]
    `(let [~handler (delay (require-and-resolve ~handler-sym))]
       (register-op! ~op-name
                     (fn [{:keys [~'transport] :as ~msg}]
                       (reply ~'transport ~msg ~result-key ~value :status :done))))))

;;; Ops with standard reply logic

(def-op "resolve-missing"      'refactor-nrepl.ns.resolve-missing/resolve-missing :candidates)
(def-op "artifact-list"        'refactor-nrepl.artifacts/artifact-list            :artifacts)
(def-op "artifact-versions"    'refactor-nrepl.artifacts/artifact-versions        :versions)
(def-op "hotload-dependency"   'refactor-nrepl.artifacts/hotload-dependency       :dependency)
(def-op "find-used-locals"     'refactor-nrepl.find.find-locals/find-used-locals  :used-locals)
(def-op "warm-ast-cache"       'refactor-nrepl.analyzer/warm-ast-cache            :ast-statuses :serialize? true :no-args? true)
(def-op "extract-definition"   'refactor-nrepl.extract-definition/extract-definition :definition :pr-str true)
(def-op "namespace-aliases"    'refactor-nrepl.ns.libspecs/namespace-aliases-response :namespace-aliases :serialize? true)
(def-op "cljr-suggest-libspecs" 'refactor-nrepl.ns.suggest-libspecs/suggest-libspecs-response :suggestions :serialize? true)
(def-op "find-used-publics"    'refactor-nrepl.find.find-used-publics/find-used-publics :used-publics :serialize? true)

;;; Ops with custom reply logic

(def ^:private find-symbol
  (delay (require-and-resolve 'refactor-nrepl.find.find-symbol/find-symbol)))

(defn- find-symbol-reply [{:keys [transport] :as msg}]
  (let [occurrences (@find-symbol msg)]
    (doseq [occurrence occurrences]
      (reply transport msg :occurrence (serialize-response msg occurrence)))
    (reply transport msg :count (count occurrences) :status :done)))

(register-op! "find-symbol" find-symbol-reply)

(def ^:private clean-ns
  (delay (require-and-resolve 'refactor-nrepl.ns.clean-ns/clean-ns)))

(def ^:private pprint-ns
  (delay (require-and-resolve 'refactor-nrepl.ns.pprint/pprint-ns)))

(defn- clean-ns-reply [{:keys [transport] :as msg}]
  (reply transport msg :ns (some-> msg (@clean-ns) (@pprint-ns)) :status :done))

(register-op! "clean-ns" clean-ns-reply)

(def ^:private rename-file-or-dir
  (delay (require-and-resolve 'refactor-nrepl.rename-file-or-dir/rename-file-or-dir)))

(defn- rename-file-or-dir-reply [{:keys [transport old-path new-path ignore-errors] :as msg}]
  (reply transport msg
         :touched (@rename-file-or-dir old-path new-path (= ignore-errors "true"))
         :status :done))

(register-op! "rename-file-or-dir" rename-file-or-dir-reply)

(defn- stubs-for-interface-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done
         :functions (serialize-response msg (stubs-for-interface msg))))

(register-op! "stubs-for-interface" stubs-for-interface-reply)

(defn- version-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :version (core/version)))

(register-op! "version" version-reply)

;;; Middleware

(defn wrap-refactor
  [handler]
  (fn [{:keys [op] :as msg}]
    ((get @refactor-nrepl-ops op handler) msg)))

(set-descriptor!
 #'wrap-refactor
 (cljs/requires-piggieback
  {:handles (zipmap (keys @refactor-nrepl-ops)
                    (repeat {:doc "See the refactor-nrepl README"
                             :returns {} :requires {}}))}))
