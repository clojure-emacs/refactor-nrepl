(ns refactor-nrepl.config)

;; NOTE: Update the readme whenever this map is changed
(def ^:dynamic *config*
  {
   ;; Verbose setting for debugging.  The biggest effect this has is
   ;; to not catch any exceptions to provide meaningful error
   ;; messages for the client.

   :debug false

   ;; When true clean-ns will remove unused symbols, otherwise just
   ;; sort etc
   :prune-ns-form true

   ;; Should clean-ns favor prefix forms in the ns macro?
   :prefix-rewriting true
   })

(defn opts-from-msg [msg]
  (into {}
        (map (fn [[k v]] (cond
                           (and (string? v) (= v "true")) [k true]
                           (string? v) [k false]
                           :else [k v]))
             (select-keys msg (keys *config*)))))

(defmacro with-config
  "Merge the override map with the default config and execute body."
  [overrides & body]
  `(binding [*config* (merge *config* (opts-from-msg ~overrides))]
     ~@body))
