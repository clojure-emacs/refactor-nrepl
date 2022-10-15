(ns refactor-nrepl.ns.libspec-allowlist
  (:require
   [clojure.java.io :as io]
   [refactor-nrepl.config :as config])
  (:import
   (clojure.lang IFn)
   (java.util.regex Pattern)))

(defn- kondo-excludes [{:keys [ns meta]}]
  (let [local-config (:clj-kondo/config meta)
        local-config (if (and (seq? local-config) (= 'quote (first local-config)))
                          (second local-config)
                          local-config)
        kondo-file (io/file ".clj-kondo" "config.edn")
        config (when (.exists kondo-file)
                 (try
                   (-> kondo-file slurp read-string)
                   (catch Exception e
                     (when (System/getenv "CI")
                       (throw e)))))]
    (concat (get-in config [:linters :unused-namespace :exclude])
            (get-in config [:config-in-ns ns :linters :unused-namespace :exclude])
            (get-in local-config [:linters :unused-namespace :exclude]))))

(defn- libspec-allowlist* [current-ns]
  (->> (kondo-excludes current-ns)
       (mapv (fn [entry]
               (if (symbol? entry)
                 (str "^" (Pattern/quote (str entry)) "$")
                 entry)))
       (into (:libspec-whitelist config/*config*))))

(def ^:private ^:dynamic ^IFn *libspec-allowlist* nil)

(defn with-memoized-libspec-allowlist* [f]
  (binding [*libspec-allowlist* (memoize libspec-allowlist*)]
    (f)))

(defn libspec-allowlist
  "Obtains a libspec allowlist, which is the result of merging clj-refactor's own `:libspec-whitelist`
  with clj-kondo's `:unused-namespace` config.

  Uses a memoized version if available."
  [current-ns]
  (or (some-> *libspec-allowlist* (.invoke current-ns))
      (libspec-allowlist* current-ns)))

(defmacro with-memoized-libspec-allowlist
  "Memoizes the libspec-allowlist internals while `body` is executing.

  _Temporary_ memoization is important because:

  * one does want to reload clj-kondo config if the user changes it, without needing a JVM restart;
  * one doesn't want that to imply a performance hit in terms of repeatedly reading clj-kondo config files."
  {:style/indent 0}
  [& body]
  `(with-memoized-libspec-allowlist*
     (fn []
       ~@body)))
