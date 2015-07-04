(ns refactor-nrepl.artifacts
  (:require [alembic.still :as alembic]
            [cheshire.core :as json]
            [clojure
             [edn :as edn]
             [string :as str]]
            [clojure.java.io :as io]
            [clojure.tools.namespace.find :as find]
            [org.httpkit.client :as http]
            [refactor-nrepl.ns.slam.hound.search :as slamhound])
  (:import java.util.Date
           java.util.jar.JarFile))

(defonce artifacts (atom {} :meta {:last-modified nil}))
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
        search-result (json/parse-string body true)]
    (map :a (-> search-result :response :docs))))

(defn- get-versions!
  "Gets all the versions from an artifact belonging to the org.clojure."
  [artifact]
  (let [{:keys [_ _ body _]} @(http/get (str "http://search.maven.org/solrsearch/select?"
                                             "q=g:%22org.clojure%22+AND+a:%22"
                                             artifact
                                             "%22&core=gav&rows=200&wt=json")
                                        {:as :text})]
    (->> (json/parse-string body true)
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

(defn artifact-list [{:keys [force]}]
  (when (or (= force "true") (stale-cache?))
    (update-artifact-cache!))
  (->> @artifacts keys list*))

(defn artifact-versions [{:keys [artifact]}]
  (->> artifact (@artifacts) list*))

(defn- make-resolve-missing-aware-of-new-deps
  "Once the deps are available on cp we still have to load them and
  reset slamhound's cache to make resolve-missing work."
  [coords repos]
  (try
    (let [dep (first (alembic/resolve-dependencies alembic/the-still coords repos nil))
          jarfile (JarFile. (:jar dep))]
      (dorun (map require (find/find-namespaces-in-jarfile jarfile)))
      (slamhound/reset))
    (catch ClassNotFoundException _
      ;; I've seen this happen after adding core.async as a dependency.
      ;; A failure here isn't a big deal, it only means that resolve-missing
      ;; isn't going to work until the namespace has been loaded manually.
      )))

(defn- ensure-quality-coordinates [coordinates]
  (let [coords (->> coordinates read-string (take 2) vec)]
    (when-not (= (count coords) 2)
      (throw (IllegalArgumentException. (str "Malformed dependency vector: "
                                             coordinates))))
    (when (stale-cache?)
      (update-artifact-cache!))
    (if-let [versions (get @artifacts (str (first coords)))]
      (when-not ((set versions) (second coords))
        (throw (IllegalArgumentException.
                (str "Version " (second coords)
                     " does not exist for " (first coords)
                     ". Available versions are " (pr-str (vec versions))))))
      (throw (IllegalArgumentException. (str "Can't find artifact '"
                                             (first coords) "'"))))))

(defn distill [coords repos]
  ;; Just so we can mock this out during testing
  (alembic/distill coords :repositories repos))

(defn hotload-dependency
  [{:keys [coordinates]}]
  (let [dependency-vector (edn/read-string coordinates)
        coords [(->> dependency-vector (take 2) vec)]
        repos {"clojars" "http://clojars.org/repo"
               "central" "http://repo1.maven.org/maven2/"}]
    (ensure-quality-coordinates coordinates)
    (distill coords repos)
    (make-resolve-missing-aware-of-new-deps coords repos)
    (str/join " " dependency-vector)))
