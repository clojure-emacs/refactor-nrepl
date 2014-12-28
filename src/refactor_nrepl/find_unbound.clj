(ns refactor-nrepl.find-unbound
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl.analyzer :refer [find-unbound-vars]]))

(defn- find-unbound-reply [{:keys [transport form] :as msg}]
  (try
    (let [unbound (find-unbound-vars (edn/read-string form))]
      (transport/send transport
                      (response-for msg
                                    :unbound (str/join " " unbound)
                                    :status :done)))
    (catch Exception e
      (transport/send transport (response-for msg :error (.getMessage e))))))

(defn wrap-find-unbound
  [handler]
  (fn [{:keys [op form] :as msg}]
    (if (= "find-unbound" op)
      (find-unbound-reply msg)
      (handler msg))))

(set-descriptor!
 #'wrap-find-unbound
 {:handles
  {"find-unbound"
   {:doc "Finds unbound vars in the input form. "
    :requires {"form" "The form on which to work"}
    :returns {"status" "done"
              "error" "an error message, intended to be displayed to the user."
              "unbound" "space separated list of unbound vars"}}}})
