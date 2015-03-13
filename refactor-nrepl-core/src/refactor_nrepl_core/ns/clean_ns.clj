(ns refactor-nrepl-core.ns.clean-ns
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
  (:require [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [refactor-nrepl.ns
             [constructor :refer [rebuild-ns-form]]
             [dependencies :refer [extract-dependencies]]
             [helpers :refer [get-ns-component]]])
  (:import [java.io FileReader PushbackReader]))

(defn- assert-no-exclude-clause
  [use-form]
  (dorun (map
          #(when (= % :exclude)
             (throw (IllegalArgumentException.
                     "Can't remove :use clause with :exclude clause!")))
          (tree-seq sequential? identity use-form)))
  use-form)

(defn- validate [ns-form]
  (assert-no-exclude-clause (get-ns-component ns-form :use))
  ns-form)

(defn read-ns-form
  [path]
  (if-let [ns-form (read-ns-decl (PushbackReader. (FileReader. path)))]
    ns-form
    (throw (IllegalArgumentException. "Malformed ns form!"))))

(defn clean-ns [path]
  (let [ns-form (read-ns-form path)
        new-ns-form (->> ns-form
                         validate
                         (extract-dependencies path)
                         (rebuild-ns-form ns-form))]
    (when-not (= ns-form new-ns-form)
      new-ns-form)))
