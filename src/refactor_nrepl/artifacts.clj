(ns refactor-nrepl.artifacts
  (:require
   [cemerick.pomegranate :as pomegranate]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.namespace.find :as find]
   [org.httpkit.client :as http]
   [refactor-nrepl.ns.slam.hound.regrow :as slamhound-regrow]
   [refactor-nrepl.ns.slam.hound.search :as slamhound]
   [version-clj.core :as versions])
  (:import java.io.File
           java.util.jar.JarFile
           java.util.zip.GZIPInputStream))

(def artifacts-file (str (io/file (System/getProperty "java.io.tmpdir")
                                  "refactor-nrepl-artifacts-cache")))

(defn get-last-modified-from-file
  "Returns last modified time in milliseconds or nil if file does not exist."
  [file]
  (let [lm (.lastModified (io/file file))]
    (if (zero? lm) nil lm)))

;;  structure here is (mostly) {"prismatic/schem" ["0.1.1" "0.2.0" ...]}
;;  The exceptions are the mvn based artifacts.  There's a ratelimit in place
;;  for those artifacts so we get the available versions on demand instead.
(defonce artifacts
  (atom (if (.exists (io/as-file artifacts-file))
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
    (if (= 200 status)
      (map :version (:recent_versions (json/read-str body :key-fn keyword)))
      (throw (ex-info (str "Unexpected response from Clojars")
                      {:status status
                       :body body})))))

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

(defn- artifact-versions* [artifact-id]
  (->> (or (get @artifacts artifact-id)
           (seq (get-mvn-versions! artifact-id))
           (get-clojars-versions! artifact-id))
       distinct
       versions/version-sort
       reverse
       list*))

(defn artifact-versions
  "Returns a sorted list of artifact version strings. The list can either come
  from the artifacts cache, the maven search api or the clojars search api in
  that order."
  [{:keys [artifact]}]
  (artifact-versions* artifact))

(defn- jar-at-the-top-of-dependency-hierarchy [new-deps]
  ;; We only need to consider the dep at the top of the hierarchy because when we
  ;; require those namespaces the rest of  the transitive deps will get pulled in
  ;; too.
  (letfn [(->jar [^File f]
            (JarFile. f))]
    (let [top-level-dep  (-> new-deps keys first)]
      (-> top-level-dep meta :file ->jar))))

(defn- make-resolve-missing-aware-of-new-deps!
  "Once the deps are available on cp we still have to load them and
  reset slamhound's cache to make resolve-missing work."
  [^JarFile jar]
  (doseq [new-namespace (find/find-namespaces-in-jarfile jar)]
    (try
      (require new-namespace)
      (catch Exception _
        ;; I've seen this happen after adding core.async as a dependency.
        ;; It also happens if you try to require namespaces that no longer work,
        ;; like compojure.handler.
        ;; A failure here isn't a big deal, it only means that resolve-missing
        ;; isn't going to work until the namespace has been loaded manually.
        )))
  (slamhound/reset)
  (slamhound-regrow/clear-cache!))

(defn- parse-coordinates [coordinates-str]
  (let [coords (try (->> coordinates-str edn/read-string (take 2) vec)
                    (catch Exception _))]
    (if (and (= (count coords) 2)
             (symbol? (first coords))
             (string? (second coords)))
      coords
      (throw (IllegalArgumentException. (str "Malformed dependency vector: "
                                             coordinates-str))))))

(defn- ensure-coordinates-exist!
  [[artifact-id artifact-version :as coordinates]]
  (when (stale-cache?)
    (update-artifact-cache!))
  (if-let [versions (artifact-versions* (str artifact-id))]
    (if ((set versions) artifact-version)
      coordinates
      (throw (IllegalArgumentException.
              (str "Version " artifact-version
                   " does not exist for " artifact-id
                   ". Available versions are " (pr-str (vec versions))))))
    (throw (IllegalArgumentException. (str "Can't find artifact '"
                                           artifact-id "'")))))

(defn- add-dependencies! [coordinates]
  ;; Just so we can mock this out during testing
  (let [repos {"clojars" "https://clojars.org/repo"
               "central" "https://repo1.maven.org/maven2/"}]
    (pomegranate/add-dependencies
     :coordinates [coordinates] :repositories repos)))

(defn- hotload-dependency! [coordinates]
  (-> (add-dependencies! coordinates)
      jar-at-the-top-of-dependency-hierarchy
      make-resolve-missing-aware-of-new-deps!))

(defn hotload-dependency
  [{:keys [coordinates]}]
  (->> coordinates
       parse-coordinates
       ensure-coordinates-exist!
       (hotload-dependency!)
       (str/join " ")))
