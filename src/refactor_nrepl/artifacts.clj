(ns refactor-nrepl.artifacts
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [org.httpkit.client :as http]
   [version-clj.core :as versions])
  (:import
   (java.util.zip GZIPInputStream)))

(def artifacts-file (str (io/file (System/getProperty "java.io.tmpdir")
                                  "refactor-nrepl-artifacts-cache")))

(defn get-last-modified-from-file
  "Returns last modified time in milliseconds or nil if file does not exist."
  [file]
  (let [lm (.lastModified (io/file file))]
    (if (zero? lm) nil lm)))

;;  structure here is {"prismatic/schem" ["0.1.1" "0.2.0" ...]}
(def artifacts (atom (if (.exists (io/file artifacts-file))
                       (->> artifacts-file slurp edn/read-string (into {}))
                       {})
                     :meta {:last-modified
                            (get-last-modified-from-file artifacts-file)}))

(def millis-per-day (* 24 60 60 1000))

(defn- get-proxy-opts
  "Generates proxy options from JVM properties for httpkit-client "
  []
  (when-let [proxy-host (some #(System/getProperty %) ["https.proxyHost" "http.proxyHost"])]
    {:proxy-host proxy-host
     :proxy-port (some->> ["https.proxyPort" "http.proxyPort"]
                          (some #(System/getProperty %)) Integer/parseInt)}))

(defn- stale-cache?
  []
  (or (empty? @artifacts)
      (if-let [last-modified (some-> artifacts meta :last-modified)]
        (neg? (- (long millis-per-day)
                 (long (- (.getTime (java.util.Date.))
                          (long last-modified)))))
        true)))

(defn- edn-read-or-nil
  "Read a form `s`. Return nil if it cannot be parsed."
  [s]
  (try (edn/read-string s)
       (catch Exception _
         ;; Ignore artifact if not readable. See #255
         nil)))

(defn- get-clojars-artifacts!
  "Returns a vector of [[some/lib \"0.1\"]...]."
  []
  (try
    (->> "https://clojars.org/repo/all-jars.clj.gz"
         io/input-stream
         GZIPInputStream.
         io/reader
         line-seq
         (keep edn-read-or-nil))
    (catch Exception _
      ;; In the event clojars is down just return an empty vector. See #136.
      [])))

(defn- get-mvn-artifacts!
  "All the artifacts under org.clojure in mvn central"
  [group-id]
  (let [search-prefix "https://search.maven.org/solrsearch/select?q=g:%22"
        search-suffix "%22+AND+p:%22jar%22&rows=2000&wt=json"
        search-url (str search-prefix group-id search-suffix)
        {:keys [_ _ body _]} @(http/get search-url (assoc (get-proxy-opts) :as :text))
        search-result (json/read-str body :key-fn keyword)]
    (->> search-result
         :response
         :docs
         (keep :a))))

(defn- get-mvn-versions!
  "Fetches all the versions of particular artifact from maven repository."
  [artifact]
  (let [[group-id artifact] (str/split artifact #"/")
        search-prefix "https://search.maven.org/solrsearch/select?q=g:%22"
        {:keys [_ _ body _]} @(http/get (str search-prefix
                                             group-id
                                             "%22+AND+a:%22"
                                             artifact
                                             "%22&core=gav&rows=100&wt=json")
                                        (assoc (get-proxy-opts) :as :text))]
    (->> (json/read-str body :key-fn keyword)
         :response
         :docs
         (keep :v))))

(defn- get-artifacts-from-mvn-central!
  []
  (let [group-ids #{"com.cognitect" "org.clojure"}]
    (mapcat (fn [group-id]
              (->> (get-mvn-artifacts! group-id)
                   (map #(vector (str group-id "/" %) nil))))
            group-ids)))

(defn get-clojars-versions!
  "Fetches all the versions of particular artifact from Clojars."
  [artifact]
  (let [{:keys [body status]} @(http/get (str "https://clojars.org/api/artifacts/"
                                              artifact))]
    (when (= 200 status)
      (->> (json/read-str body :key-fn keyword)
           :recent_versions
           (keep :version)))))

(defn- get-artifacts-from-clojars!
  []
  (reduce #(update %1 (str (first %2)) conj (second %2))
          {}
          (get-clojars-artifacts!)))

(defn- update-artifact-cache!
  []
  (let [clojars-artifacts (future (get-artifacts-from-clojars!))
        maven-artifacts (future (get-artifacts-from-mvn-central!))]
    (reset! artifacts (into @clojars-artifacts @maven-artifacts))
    (spit artifacts-file
          (binding [*print-length* nil
                    *print-level*  nil]
            (prn-str @artifacts)))
    (alter-meta! artifacts update-in [:last-modified]
                 (constantly (get-last-modified-from-file artifacts-file)))))

(defn artifact-list
  [{:keys [force]}]
  (when (or (= force "true") (stale-cache?))
    (update-artifact-cache!))
  (->> @artifacts keys list*))

(defn artifact-versions
  "Returns a sorted list of artifact version strings. The list can either come
  from the artifacts cache, the maven search api or the clojars search api in
  that order."
  [{:keys [artifact]}]
  (->> (or (get @artifacts artifact)
           (seq (get-mvn-versions! artifact))
           (get-clojars-versions! artifact))
       distinct
       versions/version-sort
       reverse
       list*))

(defn hotload-dependency
  []
  (throw (IllegalArgumentException. "Temporarily disabled until a solution for java 10 is found.")))
