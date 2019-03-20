(ns refactor-nrepl.config)

;; NOTE: Update the readme whenever this map is changed
(def ^:dynamic *config*
  {;; Verbose setting for debugging.  The biggest effect this has is
   ;; to not catch any exceptions to provide meaningful error
   ;; messages for the client.

   :debug false

   ;; When true `clean-ns` will remove unused symbols, otherwise just
   ;; sort etc
   :prune-ns-form true

   ;; Should `clean-ns` favor prefix forms in the ns macro?
   :prefix-rewriting true

   ;; Some libspecs are side-effecting and shouldn't be pruned by `clean-ns`
   ;; even if they're otherwise unused.
   ;; This seq of strings will be used as regexp patterns to match
   ;; against the libspec name.
   :libspec-whitelist ["^cljsjs"]

   ;; Regexes matching paths that are to be ignored
   :ignore-paths []})

(defn opts-from-msg [msg]
  (into {}
        (map (fn [[k v]] (cond
                           (and (string? v) (= v "true")) [k true]
                           (and (string? v) (= v "false")) [k false]
                           :else [k v]))
             (update (select-keys msg (keys *config*))
                     :ignore-paths
                     (partial map re-pattern)))))

(defmacro with-config
  "Merge the override map with the default config and execute body."
  [overrides & body]
  `(binding [*config* (merge *config* (opts-from-msg ~overrides))]
     ~@body))
