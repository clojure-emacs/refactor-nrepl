(ns refactor-nrepl.artifacts
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [version-clj.core :as versions])
  (:import
   (java.net HttpURLConnection URI)
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

(defn- http-get
  "Fetches `url` via HTTP GET and returns the body as a string, or nil on a
  non-200, blank body, timeout, or connection error.
  Honors the standard `https.proxy{Host,Port}`/`http.proxy{Host,Port}` JVM properties."
  [^String url]
  (try
    (let [conn ^HttpURLConnection (.openConnection (.toURL (URI. url)))]
      (.setConnectTimeout conn 10000)
      (.setReadTimeout conn 30000)
      (try
        (when (= 200 (.getResponseCode conn))
          (with-open [in (.getInputStream conn)]
            (let [body (slurp in :encoding "UTF-8")]
              (when-not (str/blank? body)
                body))))
        (finally
          (.disconnect conn))))
    (catch Exception _
      nil)))

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
  (let [url (str "https://search.maven.org/solrsearch/select?q=g:%22"
                 group-id
                 "%22+AND+p:%22jar%22&rows=2000&wt=json")]
    (if-let [body (http-get url)]
      (->> (json/read-str body :key-fn keyword)
           :response
           :docs
           (keep :a))
      [])))

(defn- get-mvn-versions!
  "Fetches all the versions of particular artifact from maven repository."
  [artifact]
  (let [[group-id artifact] (str/split artifact #"/")
        url (str "https://search.maven.org/solrsearch/select?q=g:%22"
                 group-id
                 "%22+AND+a:%22"
                 artifact
                 "%22&core=gav&rows=100&wt=json")]
    (if-let [body (http-get url)]
      (->> (json/read-str body :key-fn keyword)
           :response
           :docs
           (keep :v))
      [])))

(defn- get-artifacts-from-mvn-central! []
  (->> ["org.clojure" "com.cognitect"]
       (pmap (fn [group-id]
               (->> group-id
                    get-mvn-artifacts!
                    (mapv (fn [artifact]
                            [(str group-id "/" artifact),
                             nil])))))
       (reduce into [])))

(defn get-clojars-versions!
  "Fetches all the versions of particular artifact from Clojars."
  [artifact]
  (when-let [body (http-get (str "https://clojars.org/api/artifacts/" artifact))]
    (->> (json/read-str body :key-fn keyword)
         :recent_versions
         (keep :version))))

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

(defn- parse-coordinates
  "Parse a Leiningen-style dep vector string (e.g. `\"[some/lib \\\"1.0.0\\\"]\"`)
  or a deps.edn-style map literal into a deps.edn-style map."
  [^String s]
  (let [v (edn/read-string s)]
    (cond
      (map? v) v

      (and (vector? v) (symbol? (first v)) (string? (second v)))
      {(first v) {:mvn/version (second v)}}

      :else
      (throw (IllegalArgumentException.
              (str "Unrecognized coordinate format: " s))))))

(defn hotload-dependency
  "Add a Maven dependency to the running classpath.

  `:coordinates` is a Leiningen-style vector string `\"[group/artifact \\\"version\\\"]\"`
  or a deps.edn-style map literal string. Returns the input string on success."
  [{:keys [coordinates]}]
  ((requiring-resolve 'refactor-nrepl.hotload/add-libs!)
   (parse-coordinates coordinates))
  coordinates)
