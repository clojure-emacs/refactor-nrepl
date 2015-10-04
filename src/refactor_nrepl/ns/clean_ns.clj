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
  (:require [refactor-nrepl
             [config :as config]
             [core :as core]]
            [refactor-nrepl.ns
             [ns-parser :as ns-parser]
             [prune-dependencies :refer [prune-dependencies]]
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
  (assert-no-exclude-clause (core/get-ns-component ns-form :use))
  ns-form)

(defn- read-ns-meta-from-file
  "Retrieve the metadata for the ns, if there is any.

  This is preferable to just doing (meta the-ns-form) because the
  compiler, as well as libraries like clojure.test, add quite a bit of
  metadata which shouldn't be printed back out."
  [path]
  (let [meta? (-> path slurp (.replaceFirst "\\^\\{" "\\{")
                  core/ns-form-from-string second)]
    (when (map? meta?)
      meta?)))

(defn clean-ns [{:keys [path]}]
  {:pre [(seq path) (string? path) (core/source-file? path)]}
  ;; Prefix notation not supported in cljs.
  ;; We also turn it off for cljc for reasons of symmetry
  (config/with-config {:prefix-rewriting (if (or (core/cljs-file? path)
                                                 (core/cljc-file? path))
                                           false
                                           (:prefix-rewriting config/*config*))}
    (let [ns-form (with-meta (core/read-ns-form path) (read-ns-meta-from-file path))
          _ (validate ns-form)
          new-ns-form (if (get config/*config* :prune-ns-form)
                        (-> path ns-parser/parse-ns (prune-dependencies path)
                            (rebuild-ns-form ns-form))
                        (-> path ns-parser/parse-ns (rebuild-ns-form ns-form)))]
      (when-not (= ns-form new-ns-form)
        new-ns-form))))
