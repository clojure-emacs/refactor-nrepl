(ns refactor-nrepl.middleware
  (:require
   [clojure.stacktrace :refer [print-cause-trace]]
   [clojure.walk :as walk]
   [refactor-nrepl.config :as config]
   [refactor-nrepl.core :as core]
   [refactor-nrepl.descriptors :as descriptors]
   [refactor-nrepl.ns.libspec-allowlist :as libspec-allowlist]
   [refactor-nrepl.stubs-for-interface :refer [stubs-for-interface]]
   [refactor-nrepl.util :as util]))

(defn- maybe-require-piggieback
  "If piggieback is on the classpath, return `descriptor` with piggieback's
  `wrap-cljs-repl` middleware var added to its `:requires` set; otherwise
  return `descriptor` unchanged.

  Inlined here instead of calling `cider.nrepl.middleware.util.cljs/requires-piggieback`,
  which became a deprecated no-op in cider-nrepl 0.59."
  [descriptor]
  (if-let [pb (try (requiring-resolve 'cider.piggieback/wrap-cljs-repl)
                   (catch Exception _ nil))]
    (update descriptor :requires (fnil conj #{}) pb)
    descriptor))

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
     '[clojure.tools.nrepl.middleware.session]
     '[clojure.tools.nrepl.misc :refer [response-for]]
     '[clojure.tools.nrepl.transport :as transport])
    (require
     '[nrepl.middleware :refer [set-descriptor!]]
     '[nrepl.middleware.session]
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

(defmacro ^:private with-errors-being-passed-on
  "Runs `body`, replying with the error if it throws.
  An interrupted op isn't an error, though: the session middleware has
  already told the client about it, so there's nothing left to say. The
  exception isn't rethrown either, as the session's thread doesn't expect
  the code it runs to throw. `ThreadDeath` is the exception to that: it has
  to keep unwinding the thread that's being stopped."
  [transport msg & body]
  `(try
     ~@body
     (catch ThreadDeath e#
       (throw e#))
     (catch Throwable e#
       (when-not (or (util/interruption? e#) (util/interrupted?))
         (transport/send ~transport (response-for ~msg (reply-error e#)))))))

(defmacro ^:private reply
  "Sends a response to `msg` made of `kvs`.
  Nothing is sent once the op has been interrupted. By then the client has
  been told that the request is over, and whatever the op came up with is
  incomplete."
  [transport msg & kvs]
  `(libspec-allowlist/with-memoized-libspec-allowlist
     (with-errors-being-passed-on ~transport ~msg
       (config/with-config ~msg
         (let [response# (response-for ~msg ~(apply hash-map kvs))]
           (when-not (util/interrupted?)
             (transport/send ~transport response#)))))))

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
;;
;; Each op is registered under two keys: the legacy bare name (e.g.
;; `clean-ns`) and the namespaced form (`refactor/clean-ns`). The
;; namespaced form is preferred; the bare names are retained for
;; backward compatibility and will be removed in a future major release.
;; This mirrors the convention used by cider-nrepl.

(defonce refactor-nrepl-ops (atom {}))

(def op-prefix "refactor/")

(defn- register-op! [op-name reply-fn]
  (swap! refactor-nrepl-ops assoc op-name reply-fn (str op-prefix op-name) reply-fn))

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
  ;; The search itself has to run under the error handler as well (and not
  ;; just the replies), otherwise a failure never reaches the client, which is
  ;; left waiting for a response that isn't coming.
  ;; Likewise for the config: the search consults it (`:ignore-paths`,
  ;; `:debug`), and the occurrences are a lazy seq that's realized here.
  ;; With `ignore-errors` the search carries on past files it can't analyze.
  ;; Those are reported in the final message, since their occurrences are
  ;; missing from the results.
  (with-errors-being-passed-on transport msg
    (config/with-config msg
      (binding [util/*skipped-files* (atom [])]
        (let [occurrences (@find-symbol msg)]
          (doseq [occurrence occurrences]
            (reply transport msg :occurrence (serialize-response msg occurrence)))
          (reply transport msg
                 :count (count occurrences)
                 :skipped (serialize-response msg (distinct @util/*skipped-files*))
                 :status :done))))))

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

(def moved-ops
  "The ops cider-nrepl has taken over, with the ones that replace them
  there. They're deprecated and go away in 3.17.0."
  {"resolve-missing"       "cider/resolve-missing"
   "clean-ns"              "cider/clean-ns"
   "namespace-aliases"     "cider/resolve-alias"
   "cljr-suggest-libspecs" "cider/resolve-alias"})

(def interruptible-ops
  "The ops that can take a while, as they analyze the project or go out to
  the network. They run on the session's thread, like evaluations do, which
  lets the standard `interrupt` op stop them. It also means they wait for
  their turn if the session is busy.

  `warm-ast-cache` and `artifact-list` are left out on purpose: clients run
  them in the background, where they shouldn't hold up evaluations."
  #{"find-symbol" "extract-definition" "find-used-publics" "find-used-locals"
    "rename-file-or-dir" "hotload-dependency" "artifact-versions"})

(defn- bare-op-name [^String op]
  (cond-> op (.startsWith op op-prefix) (subs (count op-prefix))))

(def ^:private handler-thread-bindings
  "What the vars the session rebinds for evaluations are bound to on the
  thread that handles requests. An op has to see these on the session's
  thread too: output printed while a namespace is being analyzed would
  otherwise be sent to the client as part of the op's response, and the way
  the user set up printing in the REPL would shape the op's results."
  (into {}
        (map (fn [v] [v (.getRawRoot ^clojure.lang.Var v)]))
        [#'*out* #'*err* #'*print-length* #'*print-level* #'*print-meta*
         #'*print-namespace-maps* #'*print-readably* #'*print-dup*]))

(defn- run-op
  "Runs the op behind `reply-fn` for `msg`, on the session's thread if it's
  an interruptible one."
  [reply-fn {:keys [op session id] :as msg}]
  (let [exec (when (contains? interruptible-ops (bare-op-name op))
               (:exec (meta session)))]
    (if exec
      (exec id #(with-bindings handler-thread-bindings (reply-fn msg)) nil)
      (reply-fn msg))))

(defn wrap-refactor
  [handler]
  (fn [{:keys [op] :as msg}]
    (if-let [reply-fn (get @refactor-nrepl-ops op)]
      (run-op reply-fn msg)
      (handler msg))))

(defn- describe-op [op-name]
  (let [namespaced? (.startsWith ^String op-name op-prefix)
        bare-name (bare-op-name op-name)
        replacement (get moved-ops bare-name)
        {:keys [doc requires optional returns]} (get descriptors/ops bare-name)]
    {:doc (cond
            replacement (str "Deprecated: use cider-nrepl's `" replacement "` instead. " doc)
            namespaced? doc
            :else (str "Deprecated: use `" op-prefix op-name "` instead. " doc))
     :requires (or requires {})
     :optional (or optional {})
     :returns (or returns {})}))

(set-descriptor!
 #'wrap-refactor
 (maybe-require-piggieback
  ;; The session middleware provides the thread the interruptible ops run on.
  ;; It lives next to whichever `set-descriptor!` got required above.
  {:requires #{(-> #'set-descriptor! meta :ns ns-name (str ".session/session") symbol resolve)}
   :handles (into {}
                  (map (fn [op] [op (describe-op op)]))
                  (keys @refactor-nrepl-ops))}))
