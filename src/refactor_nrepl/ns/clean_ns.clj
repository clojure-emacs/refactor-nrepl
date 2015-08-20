(ns refactor-nrepl.ns.clean-ns
  "Contains functionality for cleaning namespaces.

  * Eliminate :use clauses
  * Sort required libraries, imports and vectors of referred symbols
  * Rewrite to favor prefix form, e.g. [clojure [string test]] instead
  of two separate libspecs
  * Raise errors if any inconsistencies are found (e.g. a libspec with more than
  one alias.
  * Remove any duplication in the :require and :import form.
  * Remove any unused required namespaces or imported classes.
  * Returns nil when nothing is changed, so the client knows not to do anything."
  (:require [refactor-nrepl.config :as config]
            [refactor-nrepl.ns
             [dependencies :refer [extract-dependencies]]
             [helpers :refer [get-ns-component read-ns-form]]
             [rebuild :refer [rebuild-ns-form]]]))

(defn- assert-no-exclude-clause
  [use-form]
  (dorun (map
          #(when (= % :exclude)
             (throw (IllegalArgumentException.
                     "Can't run clean-ns on :use clause containing :exclude!")))
          (tree-seq sequential? identity use-form)))
  use-form)

(defn- validate [ns-form]
  (assert-no-exclude-clause (get-ns-component ns-form :use))
  ns-form)

(defn- prefix-rewriting?
  "Cljs doesn't support prefix rewriting.

  If the user passes down an explicit option we use that, otherwise
  rely on the default."
  [path prefix-rewriting]
  (if (.endsWith path "cljs")
    false
    (cond
      (= prefix-rewriting "true") true
      (= prefix-rewriting "false") false
      :else (config/get-opt :prefix-rewriting))))

(defn clean-ns [{:keys [path prefix-rewriting]}]
  {:pre [(and (seq path) (string? path))]}
  (config/with-config {:prefix-rewriting (prefix-rewriting? path prefix-rewriting)}
    (let [ns-form (read-ns-form path)
          new-ns-form (-> ns-form
                          validate
                          (extract-dependencies path)
                          (rebuild-ns-form ns-form))]
      (when-not (= ns-form new-ns-form)
        new-ns-form))))
