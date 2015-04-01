(ns refactor-nrepl.config
  (:require [cider.nrepl.middleware.util.misc :refer [err-info]]
            [clojure.edn :as edn]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]))

;; NOTE: Update the readme whenever this map is changed
(def ^:private opts
  (atom
   {
    :prefix-rewriting true ; Should clean-ns favor prefix forms in the ns macro?
    }
   ))

(defn get-opt
  "Get the opt at key"
  [key]
  (get @opts key))

(defn- set-opts!
  "Sets the options for the current session. "
  [m]
  (doseq [[k v] m]
    (swap! opts assoc k v)))

(defn- check-opts [opts]
  (when-not (map? opts)
    (throw (IllegalArgumentException.
            (str "Options must be a map, got '" opts "'"))))
  (doseq [k (keys opts)]
    (when-not (get-opt k)
      (throw (IllegalArgumentException.
              (str "Unknown key in config map: " k)))))
  opts)

(defn config-reply [{:keys [transport opts] :as msg}]
  (try
    (-> opts edn/read-string check-opts set-opts!)
    (transport/send transport (response-for msg :status :done))
    (catch IllegalArgumentException e
      (response-for msg :error (.getMessage e) :status :done))
    (catch Exception e
      (transport/send transport
                      (response-for msg (err-info e :configure-error))))))

(defn wrap-config
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= op "configure")
      (config-reply msg)
      (handler msg))))

(set-descriptor!
 #'wrap-config
 {:handles
  {"configure"
   {:doc "Receives config settings that should apply for the current session."
    :requires {"opts" "A map of settings"}
    :returns {"status" "done"
              "error" "An error message, intended to be displayed to
              the user, in case of failure."}}}})
