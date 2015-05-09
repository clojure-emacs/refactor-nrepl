(ns refactor-nrepl.middleware
  (:require [cider.nrepl.middleware.util.misc :refer [err-info]]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl
             [analyzer :refer [warm-ast-cache]]
             [artifacts :refer [artifact-list artifact-versions hotload-dependency]]
             [config :refer [configure]]
             [find-symbol :refer [create-result-alist find-debug-fns find-symbol]]
             [find-unbound :refer [find-unbound-vars]]
             [plugin :as plugin]
             [stubs-for-interface :refer [stubs-for-interface]]]
            [refactor-nrepl.ns
             [clean-ns :refer [clean-ns]]
             [pprint :refer [pprint-ns]]
             [resolve-missing :refer [resolve-missing]]]))

(defmacro with-errors-being-passed-on [transport msg & body]
  `(try
     ~@body
     (catch IllegalArgumentException e#
       (transport/send
        ~transport (response-for ~msg :error (.getMessage e#) :status :done)))
     (catch IllegalStateException e#
       (transport/send
        ~transport (response-for ~msg :error (.getMessage e#) :status :done)))
     (catch Exception e#
       (transport/send
        ~transport (response-for ~msg (err-info e# :refactor-nrepl-error))))))

(defmacro reply [transport msg & kv]
  `(with-errors-being-passed-on ~transport ~msg
     (transport/send ~transport (response-for ~msg ~(apply hash-map kv)))))

(defn resolve-missing-reply [{:keys [transport] :as msg}]
  (reply transport msg :candidates (resolve-missing msg) :status :done))

(defn- find-symbol-reply [{:keys [transport] :as msg}]
  (with-errors-being-passed-on transport msg
    (let [occurrences (find-symbol msg)]
      (doseq [occurrence occurrences
              :let [response (pr-str (apply create-result-alist occurrence))]]
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
  (reply transport msg :ns (some-> path clean-ns pprint-ns) :status :done))

(defn- find-unbound-reply [{:keys [transport] :as msg}]
  (reply transport msg :unbound (find-unbound-vars msg) :status :done))

(defn- config-reply [{:keys [transport] :as msg}]
  (reply transport msg :status (and (configure msg) :done)))

(defn- version-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done :version (plugin/version)))

(defn- warm-ast-cache-reply [{:keys [transport] :as msg}]
  (reply transport msg :status (do (warm-ast-cache) :done)))

(defn- stubs-for-interface-reply [{:keys [transport] :as msg}]
  (reply transport msg :status :done
         :functions (pr-str (stubs-for-interface msg))))

(def refactor-nrepl-ops
  {"resolve-missing" resolve-missing-reply
   "find-debug-fns" find-debug-fns-reply
   "find-symbol" find-symbol-reply
   "artifact-list" artifact-list-reply
   "artifact-versions" artifact-versions-reply
   "hotload-dependency" hotload-dependency-reply
   "clean-ns" clean-ns-reply
   "configure" config-reply
   "version" version-reply
   "warm-ast-cache" warm-ast-cache-reply
   "find-unbound" find-unbound-reply
   "stubs-for-interface" stubs-for-interface-reply})

(defn wrap-refactor
  [handler]
  (fn [{:keys [op] :as msg}]
    ((get refactor-nrepl-ops op handler) msg)))

(set-descriptor!
 #'wrap-refactor
 {:handles (zipmap (keys refactor-nrepl-ops)
                   (repeat {:doc "See the refactor-nrepl README"
                            :returns {} :requires {}}))})
