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
  (:require [cider.nrepl.middleware.util.misc :refer [err-info]]
            [clojure.tools.namespace.parse :refer [read-ns-decl]]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl.ns
             [rebuild :refer [rebuild-ns-form]]
             [dependencies :refer [extract-dependencies]]
             [helpers :refer [get-ns-component]]
             [pprint :refer [pprint-ns]]]
            [refactor-nrepl.util :refer [throw-unless-clj-file]])
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
  (if-let [ns-form
           (read-ns-decl (PushbackReader. (FileReader. path)))]
    ns-form
    (throw (IllegalArgumentException. "Malformed ns form!"))))

(defn clean-ns [path]
  {:pre [(seq path)]}
  (throw-unless-clj-file path)
  (let [ns-form (read-ns-form path)
        new-ns-form (->> ns-form
                         validate
                         (extract-dependencies path)
                         (rebuild-ns-form ns-form))]
    (when-not (= ns-form new-ns-form)
      new-ns-form)))

(defn clean-ns-reply [{:keys [transport path] :as msg}]
  (try
    (let [ns (some-> path clean-ns pprint-ns)]
      (transport/send transport (response-for msg :ns ns :status :done)))
    (catch IllegalArgumentException e
      (response-for msg :error (.getMessage e) :status :done))
    (catch IllegalStateException e
      (response-for msg :error (.getMessage e) :status :done))
    (catch Exception e
      (transport/send transport
                      (response-for msg (err-info e :clean-ns-error))))))

(defn wrap-clean-ns
  [handler]
  (fn [{:keys [op] :as msg}]
    (cond
     (= op "clean-ns") (clean-ns-reply msg)
     :else
     (handler msg))))

(set-descriptor!
 #'wrap-clean-ns
 {:handles
  {"clean-ns"
   {:doc "Various cleanups on the ns form."
    :requires {"path" "The absolute path to the file to clean."}
    :returns {"status" "done"
              "error" "An error message, intended to be displayed to
              the user, in case of failure."
              "ns" "The entire (ns ..) form in pristine condition, or nil if nothing was done."}}}})
