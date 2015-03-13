(ns refactor-nrepl.middleware
  (:require [alembic.still :refer [distill]]
            [clojure
             [edn :as edn]
             [string :as str]]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [refactor-nrepl-core
             [analyzer :refer [find-unbound-vars]]
             [artifacts :refer [artifact-versions artifacts-list]]
             [find-symbol :refer [find-debug-fns find-symbol]]]
            [refactor-nrepl-core.ns
             [clean-ns :refer [clean-ns]]
             [pprint :refer [pprint-ns]]
             [resolve-missing :refer [resolve-missing]]]
            [refactor-nrepl.bootstrap :refer [repositories init-classloader]]
            [classlojure.core :refer [eval-in]]))

(defonce cl (init-classloader))

(defmacro with-errors-being-passed-on [transport msg & body]
  `(try
     ~@body
     (catch Exception e#
       (transport/send ~transport
                       (response-for ~msg :error (.getMessage e#) :status :done)))))

(defn reply [transport msg response]
  (with-errors-being-passed-on transport msg
    (transport/send transport (response-for msg response))))

(defn- find-symbol-reply [{:keys [transport] :as msg}]
  (with-errors-being-passed-on transport msg
    (let [occurrences (eval-in cl find-symbol msg)]
      (doseq [occurrence occurrences]
        (transport/send transport (response-for msg :occurrence (pr-str occurrence))))
      (transport/send transport (response-for msg :count (count occurrences)
                                              :status :done)))))

(defn hotload-dependency [{:keys [coordinates]}]
  (let [dependency-vector (edn/read-string coordinates)
        coords [(->> dependency-vector (take 2) vec)]]
    (when-not (= (-> coords first count) 2)
      (throw (IllegalArgumentException. (str "Malformed dependency vector: " coordinates))))
    (distill coords :repositories repositories)
    dependency-vector))


(defn- hotload-dependency-reply [{:keys [transport] :as msg}]
  (reply transport msg {:dependency (pr-str (hotload-dependency msg)) :status :done }))

(defn- find-debug-fns-reply [{:keys [transport] :as msg}]
  (reply transport msg {:value (seq (eval-in cl 'find-debug-fns msg)) :status :done}))

(defn- artifact-list-reply [{:keys [transport] :as msg}]
  (reply transport msg {:artifacts (eval-in cl 'artifacts-list msg) :status :done}))

(defn- artifact-versions-reply [{:keys [transport] :as msg}]
  (reply transport msg {:versions (eval-in cl 'artifact-versions msg) :status :done}))

(defn- find-unbound-reply [{:keys [transport ns] :as msg}]
  (reply transport msg {:unbound (str/join " " (eval-in cl 'find-unbound-vars ns)) :status :done}))

(defn- clean-ns-reply [{:keys [transport path] :as msg}]
  (reply transport msg {:ns (eval-in cl '(some-> path clean-ns pprint-ns)) :status :done}))

(defn- resolve-missing-reply [{sym :symbol transport :transport :as msg}]
  (reply transport msg {:candidates (eval-in cl 'resolve-missing msg) :status :done}))

(def refactor-nrepl-ops
  {"resolve-missing" resolve-missing-reply
   "find-debug-fns" find-debug-fns-reply
   "find-symbol" find-symbol-reply
   "artifact-list" artifact-list-reply
   "artifact-versions" artifact-versions-reply
   "hotload-dependency" hotload-dependency-reply
   "clean-ns" clean-ns-reply
   "find-unbound" find-unbound-reply})

(defn wrap-refactor
  [handler]
 (fn [{:keys [op] :as msg}]
   ((get refactor-nrepl-ops op handler) msg)))

(set-descriptor!
 #'wrap-refactor
 {:handles (zipmap (keys refactor-nrepl-ops)
                   (repeat {:doc "See the refactor-nrepl README" :returns {} :requires {}}))})
