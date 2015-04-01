(ns refactor-nrepl.find-unbound
  (:require [cider.nrepl.middleware.util.misc :refer [err-info]]
            [clojure.string :as str]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl.analyzer :refer [find-unbound-vars]]))

(defn- find-unbound-reply [{:keys [transport ns] :as msg}]
  (try
    (let [unbound (find-unbound-vars ns)]
      (transport/send transport
                      (response-for msg
                                    :unbound (str/join " " unbound)
                                    :status :done)))
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
   {:doc "Finds unbound vars in the input ns."
    :requires {"ns" "The ns containing unbound vars"}
    :returns {"status" "done"
              "error" "an error message, intended to be displayed to the user."
              "unbound" "space separated list of unbound vars"}}}})
