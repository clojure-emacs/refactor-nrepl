(ns refactor-nrepl.middleware
  (:require [cider.nrepl.middleware.util.cljs :as cljs]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]
            [refactor-nrepl.config :as config]
            [refactor-nrepl.core :as core]
            [refactor-nrepl.ns.libspecs :refer [namespace-aliases]]
            [refactor-nrepl.stubs-for-interface :refer [stubs-for-interface]]
            [clojure.walk :as walk]))

(defn- require-and-resolve [sym]
  (require (symbol (namespace sym)))
  (resolve sym))

(defn err-info
  [ex status]
  {:ex (str (class ex))
   :err (with-out-str (print-cause-trace ex))
   :status #{status :done}})

(defmacro ^:private with-errors-being-passed-on [transport msg & body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (transport/send
        ~transport (response-for ~msg :error (.toString e#) :status :done)))
     (catch IllegalArgumentException e#
       (transport/send
        ~transport (response-for ~msg :error (.getMessage e#) :status :done)))
     (catch IllegalStateException e#
       (transport/send
        ~transport (response-for ~msg :error (.getMessage e#) :status :done)))
     (catch Exception e#
       (transport/send
        ~transport (response-for ~msg (err-info e# :refactor-nrepl-error))))
     (catch Error e#
       (transport/send
        ~transport (response-for ~msg (err-info e# :refactor-nrepl-error))))))

(defmacro ^:private reply [transport msg & kvs]
  `(with-errors-being-passed-on ~transport ~msg
     (config/with-config ~msg
       (transport/send ~transport
                       (response-for ~msg ~(apply hash-map :status :done kvs))))))

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

(defn- serialize-response [{:keys [serialization-format] :as msg} response]
  (binding [*print-length* nil
            *print-level* nil]
    (condp = serialization-format
      "edn" (pr-str response)
      "bencode" (bencode-friendly-data response)
      (pr-str response) ; edn as default
      )))

(def ^:private resolve-missing
  (delay
   (require-and-resolve 'refactor-nrepl.ns.resolve-missing/resolve-missing)))

(defn resolve-missing-reply [{:keys [transport] :as msg}]
  (reply transport msg :candidates (@resolve-missing msg) :status :done))

(def ^:private find-symbol
  (delay
   (require-and-resolve 'refactor-nrepl.find.find-symbol/find-symbol)))

(defn- find-symbol-reply [{:keys [transport] :as msg}]
  (config/with-config msg
    (with-errors-being-passed-on transport msg
      (let [occurrences (@find-symbol msg)]
        (doseq [occurrence occurrences
                :let [response (serialize-response msg occurrence)]]
          (transport/send transport
                          (response-for msg :occurrence response)))
        (transport/send transport (response-for msg :count (count occurrences)
                                                :status :done))))))

(def ^:private artifact-list
  (delay (require-and-resolve 'refactor-nrepl.artifacts/artifact-list)))

(def ^:private artifact-versions
  (delay (require-and-resolve 'refactor-nrepl.artifacts/artifact-versions)))

(def ^:private hotload-dependency
  (delay (require-and-resolve 'refactor-nrepl.artifacts/hotload-dependency)))

(defn- artifact-list-reply [{:keys [transport] :as msg}]
  (reply transport msg :artifacts (@artifact-list msg) :status :done))

(defn- artifact-versions-reply [{:keys [transport] :as msg}]
  (reply transport msg :versions (@artifact-versions msg) :status :done))

(defn- hotload-dependency-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :dependency (@hotload-dependency msg)))

(def ^:private clean-ns
  (delay
   (require-and-resolve 'refactor-nrepl.ns.clean-ns/clean-ns)))

(def ^:private pprint-ns
  (delay
   (require-and-resolve 'refactor-nrepl.ns.pprint/pprint-ns)))

(defn- clean-ns-reply [{:keys [transport path] :as msg}]
  (reply transport msg :ns (some-> msg (@clean-ns) (@pprint-ns)) :status :done))

(def ^:private find-used-locals
  (delay
   (require-and-resolve 'refactor-nrepl.find.find-locals/find-used-locals)))

(defn- find-used-locals-reply [{:keys [transport] :as msg}]
  (reply transport msg :used-locals (@find-used-locals msg)))

(defn- version-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :version (core/version)))

(def ^:private warm-ast-cache
  (delay
   (require-and-resolve 'refactor-nrepl.analyzer/warm-ast-cache)))

(defn- warm-ast-cache-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done
         :ast-statuses (serialize-response msg (@warm-ast-cache))))

(def ^:private warm-macro-occurrences-cache
  (delay (require-and-resolve 'refactor-nrepl.find.find-macros/warm-macro-occurrences-cache)))

(defn- warm-macro-occurrences-cache-reply [{:keys [transport] :as msg}]
  (@warm-macro-occurrences-cache)
  (reply transport msg :status :done))

(defn- stubs-for-interface-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done
         :functions (serialize-response msg (stubs-for-interface msg))))

(def ^:private extract-definition
  (delay
   (require-and-resolve 'refactor-nrepl.extract-definition/extract-definition)))

(defn- extract-definition-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :definition (pr-str (@extract-definition msg))))

(def ^:private rename-file-or-dir
  (delay
   (require-and-resolve 'refactor-nrepl.rename-file-or-dir/rename-file-or-dir)))

(defn- rename-file-or-dir-reply [{:keys [transport old-path new-path] :as msg}]
  (reply transport msg :touched (@rename-file-or-dir old-path new-path)
         :status :done))

(defn- namespace-aliases-reply [{:keys [transport] :as msg}]
  (reply transport msg
         :namespace-aliases (serialize-response msg (namespace-aliases))
         :status :done))

(def ^:private find-used-publics
  (delay (require-and-resolve 'refactor-nrepl.find.find-used-publics/find-used-publics) ))

(defn- find-used-publics-reply [{:keys [transport] :as msg}]
  (reply transport msg
         :used-publics (serialize-response msg (@find-used-publics msg)) :status :done))

(def refactor-nrepl-ops
  {
   "artifact-list" artifact-list-reply
   "artifact-versions" artifact-versions-reply
   "clean-ns" clean-ns-reply
   "extract-definition" extract-definition-reply
   "find-symbol" find-symbol-reply
   "find-used-locals" find-used-locals-reply
   "hotload-dependency" hotload-dependency-reply
   "namespace-aliases" namespace-aliases-reply
   "rename-file-or-dir" rename-file-or-dir-reply
   "resolve-missing" resolve-missing-reply
   "stubs-for-interface" stubs-for-interface-reply
   "find-used-publics" find-used-publics-reply
   "version" version-reply
   "warm-ast-cache" warm-ast-cache-reply
   "warm-macro-occurrences-cache" warm-macro-occurrences-cache-reply
   })

(defn wrap-refactor
  [handler]
  (fn [{:keys [op] :as msg}]
    ((get refactor-nrepl-ops op handler) msg)))

(set-descriptor!
 #'wrap-refactor
 (cljs/requires-piggieback
  {:handles (zipmap (keys refactor-nrepl-ops)
                    (repeat {:doc "See the refactor-nrepl README"
                             :returns {} :requires {}}))}))
