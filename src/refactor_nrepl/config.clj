(ns refactor-nrepl.config
  (:require [clojure.edn :as edn]))

;; NOTE: Update the readme whenever this map is changed
(def ^:private opts
  (atom
   {:prefix-rewriting true ; Should clean-ns favor prefix forms in the ns macro?
    ;; Verbose setting for debugging.  The biggest effect this has is
    ;; to not catch any exceptions to provide meaningful error
    ;; messages for the client.
    :debug false
    }))

;; TODO Get rid of the other stuff and start passing down config
;; settings on each call instead of keeping state.
;; NOTE: Update the readme whenever this map is changed
(def ^:dynamic *config*
  {:prefix-rewriting true ; Should clean-ns favor prefix forms in the ns macro?
   ;; Verbose setting for debugging.  The biggest effect this has is
   ;; to not catch any exceptions to provide meaningful error
   ;; messages for the client.
   :debug false
   })

(defn get-opt
  "Get the opt at key"
  [key]
  (get @opts key))

(defn contains-opt?
  "Checks if key is present in the opts map."
  [key]
  (contains? @opts key))

(defn- set-opts!
  "Sets the options for the current session."
  [m]
  (doseq [[k v] m]
    (swap! opts assoc k v)))

(defn- check-opts [opts]
  (when-not (map? opts)
    (throw (IllegalArgumentException.
            (str "Options must be a map, got '" opts "'"))))
  (doseq [k (keys opts)]
    (when-not (contains-opt? k)
      (throw (IllegalArgumentException.
              (str "Unknown key in config map: " k)))))
  opts)

(defn set-opt!
  "Set the option under key k to value v."
  [k v]
  (swap! opts assoc k v))

(defn configure [{:keys [opts]}]
  (-> opts edn/read-string check-opts set-opts!))

(defmacro with-config
  "Merge the override map with the default config and execute body."
  [overrides & body]
  `(binding [refactor-nrepl.config/*config*
             (merge refactor-nrepl.config/*config* ~overrides)]
     ~@body))
