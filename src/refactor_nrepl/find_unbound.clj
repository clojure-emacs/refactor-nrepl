(ns refactor-nrepl.find-unbound
  (:require [cider.nrepl.middleware.util.misc :refer [err-info]]
            [clojure.set :as set]
            [clojure.tools.analyzer.ast :refer [nodes]]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl
             [analyzer :refer [ns-ast]]
             [util :refer :all]]))

(defn- find-unbound-vars [{:keys [file line column]}]
  {:pre [(number? line)
         (number? column)
         (not-empty file)]}
  (throw-unless-clj-file file)
  (let [ast (-> file slurp ns-ast)
        selected-sexpr (->> ast
                            (top-level-form-index line column)
                            (nth ast)
                            nodes
                            (filter (partial node-at-loc? line column))
                            first)]
    (set/intersection (->> selected-sexpr :env :locals keys set)
                      (->> selected-sexpr
                           nodes
                           (filter #(= :local (:op %)))
                           (map :form)
                           set))))

(defn- find-unbound-reply [{:keys [transport] :as msg}]
  (try
    (transport/send
     transport
     (response-for msg :unbound (into '() (find-unbound-vars msg)) :status :done))
    (catch IllegalArgumentException e
      (transport/send transport
                      (response-for msg :error (.getMessage e) :status :done)))
    (catch Exception e
      (transport/send transport (response-for msg (err-info e :find-unbound-error))))))

(defn wrap-find-unbound
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= "find-unbound" op)
      (find-unbound-reply msg)
      (handler msg))))

(set-descriptor!
 #'wrap-find-unbound
 {:handles
  {"find-unbound"
   {:doc "Finds vars which would be unbound when the nearest enclosing form is moved to be a top level form."
    :requires {"file" "Path to the file in the project to work on"
               "line" "The line number defining the point to find the 'nearest enclosing form'"
               "column" "The column number defining the point to find the 'nearest enclosing form'"}
    :returns {"status" "done"
              "error" "an error message, intended to be displayed to the user."
              "unbound" "list of unbound vars"}}}})
