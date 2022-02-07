(ns refactor-nrepl.ns.libspec-allowlist
  (:require
   [clojure.java.io :as io]
   [refactor-nrepl.config :as config])
  (:import
   (java.util.regex Pattern)))

(defn- libspec-allowlist* []
  (let [kondo-file (io/file ".clj-kondo" "config.edn")
        exclude (when (.exists kondo-file)
                  (try
                    (-> kondo-file slurp read-string :linters :unused-namespace :exclude)
                    (catch Exception e
                      (when (System/getenv "CI")
                        (throw e)))))]
    (->> exclude
         (mapv (fn [entry]
                 (if (symbol? entry)
                   (str "^" (Pattern/quote (str entry)) "$")
                   entry)))
         (into (:libspec-whitelist config/*config*)))))

(def ^:private ^:dynamic *libspec-allowlist* nil)

(defn with-memoized-libspec-allowlist* [f]
  (binding [*libspec-allowlist* (memoize libspec-allowlist*)]
    (f)))

(defn libspec-allowlist
  "Obtains a libspec allowlist, which is the result of merging clj-refactor's own `:libspec-whitelist`
  with clj-kondo's `:unused-namespace` config.

  Uses a memoized version if available."
  []
  (or *libspec-allowlist*
      (libspec-allowlist*)))

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
