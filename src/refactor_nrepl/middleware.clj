(ns refactor-nrepl.middleware
  (:require [cider.nrepl.middleware.util
             [cljs :as cljs]
             [misc :refer [err-info]]]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl
             [analyzer :refer [warm-ast-cache]]
             [artifacts :refer [artifact-list artifact-versions hotload-dependency]]
             [config :as config]
             [extract-definition :refer [extract-definition]]
             [plugin :as plugin]
             [rename-file-or-dir :refer [rename-file-or-dir]]
             [stubs-for-interface :refer [stubs-for-interface]]]
            [refactor-nrepl.find
             [find-symbol :refer [create-result-alist find-debug-fns find-symbol]]
             [find-locals :refer [find-used-locals]]]
            [refactor-nrepl.ns
             [clean-ns :refer [clean-ns]]
             [namespace-aliases :refer [namespace-aliases]]
             [pprint :refer [pprint-ns]]
             [resolve-missing :refer [resolve-missing]]]))

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
        ~transport (response-for ~msg (err-info e# :refactor-nrepl-error))))))

(defmacro ^:private reply [transport msg & kvs]
  `(with-errors-being-passed-on ~transport ~msg
     (config/with-config ~msg
       (transport/send ~transport
                       (response-for ~msg ~(apply hash-map :status :done kvs))))))

(defn- serialize-response [{:keys [serialization-format] :as msg} response]
  (condp = serialization-format
    "edn" (pr-str response)
    "bencode" response
    (pr-str response) ; edn as default
    ))

(defn resolve-missing-reply [{:keys [transport] :as msg}]
  (reply transport msg :candidates (resolve-missing msg) :status :done))

(defn- find-symbol-reply [{:keys [transport] :as msg}]
  (with-errors-being-passed-on transport msg
    (let [occurrences (find-symbol msg)]
      (doseq [occurrence occurrences
              :let [response (serialize-response msg (apply create-result-alist occurrence))]]
        (transport/send transport
                        (response-for msg :occurrence response)))
      (transport/send transport (response-for msg :count (count occurrences)
                                              :status :done)))))

(defn- find-debug-fns-reply [{:keys [transport] :as msg}]
  (reply transport msg :value (find-debug-fns msg) :status :done))

(defn- artifact-list-reply [{:keys [transport] :as msg}]
  (reply transport msg :artifacts (artifact-list msg) :status :done))

(defn- artifact-versions-reply [{:keys [transport] :as msg}]
  (reply transport msg :versions (artifact-versions msg) :status :done))

(defn- hotload-dependency-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :dependency (hotload-dependency msg)))

(defn- clean-ns-reply [{:keys [transport path] :as msg}]
  (reply transport msg :ns (some-> msg clean-ns (pprint-ns path)) :status :done))

(defn- find-used-locals-reply [{:keys [transport] :as msg}]
  (reply transport msg :used-locals (find-used-locals msg)))

(defn- clojure-version-reply [{:keys [transport] :as msg}]
  (reply transport msg :clojure-version (clojure-version)))

(defn- version-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :version (plugin/version)))

(defn- warm-ast-cache-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done
         :ast-statuses (serialize-response msg (warm-ast-cache))))

(defn- stubs-for-interface-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done
         :functions (serialize-response msg (stubs-for-interface msg))))

(defn- extract-definition-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :definition (pr-str (extract-definition msg))))

(defn- rename-file-or-dir-reply [{:keys [transport old-path new-path] :as msg}]
  (reply transport msg :touched (rename-file-or-dir old-path new-path)
         :status :done))

(defn- namespace-aliases-reply [{:keys [transport] :as msg}]
  (reply transport msg
         :namespace-aliases (serialize-response msg (namespace-aliases))
         :status :done))

(def refactor-nrepl-ops
  {
   "artifact-list" artifact-list-reply
   "artifact-versions" artifact-versions-reply
   "clean-ns" clean-ns-reply
   "clojure-version" clojure-version-reply
   "extract-definition" extract-definition-reply
   "find-debug-fns" find-debug-fns-reply
   "find-symbol" find-symbol-reply
   "find-used-locals" find-used-locals-reply
   "hotload-dependency" hotload-dependency-reply
   "namespace-aliases" namespace-aliases-reply
   "rename-file-or-dir" rename-file-or-dir-reply
   "resolve-missing" resolve-missing-reply
   "stubs-for-interface" stubs-for-interface-reply
   "version" version-reply
   "warm-ast-cache" warm-ast-cache-reply
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
