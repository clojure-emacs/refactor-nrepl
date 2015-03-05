(ns refactor-nrepl.ns.resolve-missing
  (:require [cider.nrepl.middleware.info :refer [info-clj]]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl.ns.slam.hound.regrow :as slamhound]))

(defn- candidates [sym]
  (seq (concat (slamhound/candidates :import sym [] {})
               (slamhound/candidates :refer sym [] {}))))

(defn- get-type [sym]
  (let [info (info-clj 'user sym)]
    (if-let [clazz (:class info)]
      (cond
        ((set (:interfaces info)) 'clojure.lang.IType) :type
        ((set (:interfaces info)) 'clojure.lang.IRecord) :type
        :else :class)                   ; interfaces are included here
      :ns)))

(defn- collate-type-info
  [candidates]
  (map #(list % (get-type %)) candidates))

(defn- get-candidates [sym]
  (some->> sym
           candidates
           collate-type-info
           print-str))

(defn resolve-missing-reply [{sym :symbol transport :transport :as msg}]
  (try
    (when-not (and sym (string? sym) (seq sym))
      (throw (IllegalArgumentException. (str "Invalid input to resolve missing: '" sym "'"))))
    (transport/send transport
                    (response-for msg
                                  :candidates (-> sym symbol get-candidates)
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
    :requires {"symbol" "A symbol to resolve on the classpath for use
    in a require or import statement"}
    :returns {"status" "done"
              "error" "An error message, intended to be displayed to
              the user, in case of failure."
              "candidates" "An alist of  (symbol . type), where type is :type for deftypes and records, :class for classes and interfaces and :ns for namespaces."}}}})
