(ns refactor-nrepl.ns.resolve-missing
  (:require [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl.ns.helpers :refer [suffix]]
            [slam.hound.regrow :as slamhound]))

(defn- candidates [type sym]
  (let [res (slamhound/candidates type sym [] {})]
    (when-not (empty? res)
      res)))

(defn- capitalized? [x]
  (and (string? x) (Character/isUpperCase ^Character (first (name x)))))

(defn symbol-is-class?
  [sym]
  (let [sym-str (str sym)
        suffix? (suffix sym-str)]
    (or (capitalized? sym-str)
        (capitalized? suffix?))))

(defn- get-candidates [sym]
  (some->> (if (symbol-is-class? sym)
             (candidates :import sym)
             (candidates :refer sym))
           (into [])
           (interpose " ")
           (apply str)))

(defn resolve-missing-reply [{sym :symbol transport :transport :as msg}]
  (try
    (transport/send transport
                    (response-for msg
                                  :candidates (-> sym symbol get-candidates)
                                  :type (if (symbol-is-class? sym)
                                          :import :require)
                                  :status :done))
    (catch Exception e
      (transport/send transport
                      (response-for msg :error (.getMessage e) :status :done)))))

(defn wrap-resolve-missing
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= op "resolve-missing")
      (resolve-missing-reply msg)
      (handler msg))))

(set-descriptor!
 #'wrap-resolve-missing
 {:handles
  {"resolve-missing"
   {:doc "Resolves a missing symbol to provide candidate imports."
    :requires {"symbol" "Either a var or class to import."}
    :returns {"status" "done"
              "error" "An error message, intended to be displayed to
              the user, in case of failure."
              "candidates" "A space separated listed of candidates"
              "type" "Either 'require' or 'import', so the client knows where to
put any newly created libspec."}}}})
