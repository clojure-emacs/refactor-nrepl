(ns refactor-nrepl.artifacts
  (:require [alembic.still :refer [distill]]
            [clojure
             [edn :as edn]
             [string :as str]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.nrepl
             [middleware :refer [set-descriptor!]]
             [misc :refer [response-for]]
             [transport :as transport]]
            [org.httpkit.client :as http])
  (:import java.util.Date))

(def artifacts (atom {} :meta {:last-modified nil}))
(def millis-per-day (* 24 60 60 1000))

(defn get-artifacts-from-clojars!
  "Returns a vector of [[some/lib \"0.1\"]...]."
  []
  (->> "https://clojars.org/repo/all-jars.clj"
       java.net.URL.
       io/reader
       line-seq
       (map edn/read-string)))

(defn add-artifacts-from-clojars! []
  (->> (get-artifacts-from-clojars!)
       (map #(swap! artifacts update-in [(str (first %))] conj (second %)))
       dorun))

(defn- stale-cache? []
  (or (empty? @artifacts)
      (if-let [last-modified (some-> artifacts meta :last-modified .getTime)]
        (neg? (- millis-per-day (- last-modified (.getTime (java.util.Date.)))))
        true)))

(defn- get-all-clj-artifacts!
  "All the artifacts under org.clojure in mvn central"
  []
  (let [search-url "http://search.maven.org/solrsearch/select?q=g:%22org.clojure%22+AND+p:%22jar%22&rows=2000&wt=json"
        {:keys [_ _ body _]} @(http/get search-url {:as :text})
        search-result (json/read-str body :key-fn keyword)]
    (map :a (-> search-result :response :docs))))

(defn- get-versions!
  "Gets all the versions from an artifact belonging to the org.clojure."
  [artifact]
  (let [{:keys [_ _ body _]} @(http/get (str "http://search.maven.org/solrsearch/select?"
                                             "q=g:%22org.clojure%22+AND+a:%22"
                                             artifact
                                             "%22&core=gav&rows=200&wt=json")
                                        {:as :text})]
    (->> (json/read-str body :key-fn keyword)
         :response
         :docs
         (map :v)
         doall)))

(defn- collate-artifact-and-versions [artifact]
  (->> artifact
       get-versions!
       (vector (str "org.clojure/" artifact))))

(defn- add-artifact [[artifact versions]]
  (swap! artifacts update-in [artifact] (constantly versions)))

(defn- add-artifacts [artifacts]
  (->> artifacts
       (partition-all 2)
       (map #(future (->> %
                          (map collate-artifact-and-versions)
                          (map add-artifact)
                          dorun)))))

(defn- get-artifacts-from-mvn-central! []
  (add-artifacts (get-all-clj-artifacts!)))

(defn- update-artifact-cache! []
  (let [mvn-central-futures (get-artifacts-from-mvn-central!)
        clojars-future (future (add-artifacts-from-clojars!))]
    (dorun (map deref mvn-central-futures))
    @clojars-future)
  (alter-meta! artifacts update-in [:last-modified]
               (constantly (java.util.Date.))))

(defn- artifacts-list [{:keys [transport force] :as msg}]
  (when (or (= force "true") (stale-cache?))
    (update-artifact-cache!))
  (let [names (->> @artifacts
                   keys
                   (interpose " ")
                   (apply str))]
    (transport/send transport (response-for msg :value names :status :done))))

(defn- artifact-versions [{:keys [transport artifact] :as msg}]
  (when (stale-cache?)
    (update-artifact-cache!))
  (let [versions (->> artifact (@artifacts) (interpose " ") (apply str))]
    (transport/send transport (response-for msg :value versions  :status :done))))

(defn- hotload-dependency
  [{:keys [transport coordinates] :as msg}]
  (try
    (let [dependency-vector (edn/read-string coordinates)
          coords [(->> dependency-vector (take 2) vec)]
          repos {"clojars" "http://clojars.org/repo"
                 "central" "http://repo1.maven.org/maven2/"}]
      (when-not (= (-> coords first count) 2)
        (throw (IllegalArgumentException. (str "Malformed dependency vector: "
                                               coordinates))))
      (distill coords :repositories repos)
      (transport/send transport
                      (response-for msg :status :done
                                    :dependency (str/join " " dependency-vector))))
    (catch Exception e
      (transport/send transport (response-for msg :error (.getMessage e)
                                              :status :done)))))

(defn wrap-artifacts
  [handler]
  (fn [{:keys [op] :as msg}]
    (cond (= op "artifact-list") (artifacts-list msg)
          (= op "artifact-versions") (artifact-versions msg)
          (= op "hotload-dependency") (hotload-dependency msg)
          :else
          (handler msg))))

(set-descriptor!
 #'wrap-artifacts
 {:handles
  {"artifact-list"
   {:doc "Returns a list of all the artifacts avilable on clojars, and a select few from mvn central.  The value is is cached for one day, or until the repl session is terminated."
    :requires {}
    :optional {"force" "which, if present, indicates whether we should force an update of the list of artifacts, rather than use the cache."}
    :returns {"status" "done"
              "value" "string containing artifacts, separated by spaces."}}
   "artifact-versions"
   {:doc "Get all the available versions for an artifact."
    :requires {"artifact" "the artifact whose versions we're interested in"}
    :optional {}
    :returns {"status" "done"
              "value" "string containing artifact versions, separated by spaces."}}
   "hotload-dependency"
   {:doc "Adds non-conflicting dependency changes to active nrepl."
    :requires {:coordinates "A leiningen coordinate vector"}
    :returns {"status" "done" "dependency" "the coordinate vector that was hotloaded"}}}})
