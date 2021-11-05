(ns refactor-nrepl.config)

;; NOTE: Update the readme whenever this map is changed
(def ^:dynamic *config*
  {;; Verbose setting for debugging.  The biggest effect this has is
   ;; to not catch any exceptions to provide meaningful error
   ;; messages for the client.

   :debug false

   ;; if `true`:
   ;;   * files that can't be `read`, `require`d or analyzed (with `tools.analyzer`) will be ignored,
   ;;     instead of aborting the early phases of refactor-nrepl execution.
   ;;   * ops like `find-symbol` will carry on even if there is broken namespace which we can not build AST for.
   ;; Setting `false` will be more strict, yielding possibly more correct usage,
   ;; but it also needs that `:ignore-paths` is correctly set, that all namespaces are valid,
   ;; that tools.analyzer is able to analyze all of them, etc
   :ignore-errors true

   ;; When true `clean-ns` will remove unused symbols, otherwise just
   ;; sort etc
   :prune-ns-form true

   ;; Should `clean-ns` favor prefix forms in the ns macro?
   :prefix-rewriting false

   ;; Should `pprint-ns` place a newline after the `:require` and `:import` tokens?
   :insert-newline-after-require true

   ;; Some libspecs are side-effecting and shouldn't be pruned by `clean-ns`
   ;; even if they're otherwise unused.
   ;; This seq of strings will be used as regexp patterns to match
   ;; against the libspec name.
   :libspec-whitelist ["^cljsjs"]

   ;; Regexes matching paths that are to be ignored
   :ignore-paths []

   ;; Will be forwarded to clojure.pprint/*print-right-margin* when pprinting ns forms.
   ;; You can set it to nil for disabling line wrapping.
   ;; See also: :print-miser-width
   :print-right-margin 72

   ;; Will be forwarded to clojure.pprint/*print-miser-width* when pprinting ns forms.
   ;; You can set it to nil for disabling line wrapping.
   ;; See also: :print-right-margin
   :print-miser-width 40})

(defn opts-from-msg [msg]
  (into {}
        (map (fn [[k v]] (cond
                           (and (string? v) (= v "true")) [k true]
                           (and (string? v) (= v "false")) [k false]
                           (and (string? v) (= v "nil")) [k nil]
                           :else [k v]))
             (update (select-keys msg (keys *config*))
                     :ignore-paths
                     (partial map re-pattern)))))

(defmacro with-config
  "Merge the override map with the default config and execute body."
  [overrides & body]
  `(binding [*config* (merge *config* (opts-from-msg ~overrides))]
     ~@body))
