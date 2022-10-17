(ns refactor-nrepl.ns.libspec-allowlist
  (:require
   [clojure.java.io :as io]
   [refactor-nrepl.config :as config])
  (:import
   (clojure.lang IFn)
   (java.util.regex Pattern)))

(defn maybe-unwrap-quote [obj]
  (if (and (seq? obj) (= 'quote (first obj)))
    (second obj)
    obj))

(defn- kondo-excludes [{namespace-name :ns
                        ns-meta        :meta}]
  (let [linter-path [:linters :unused-namespace :exclude]
        local-config (-> ns-meta :clj-kondo/config maybe-unwrap-quote)
        kondo-file (io/file ".clj-kondo" "config.edn")
        config (when (.exists kondo-file)
                 (try
                   (-> kondo-file slurp read-string)
                   (catch Exception e
                     (when (System/getenv "CI")
                       (throw e)))))]
    (reduce into [(get-in config linter-path)
                  (get-in config (into [:config-in-ns namespace-name] linter-path))
                  (get-in local-config linter-path)])))

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
