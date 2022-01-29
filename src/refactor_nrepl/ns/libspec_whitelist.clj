(ns refactor-nrepl.ns.libspec-whitelist
  (:require
   [clojure.java.io :as io]
   [refactor-nrepl.config :as config])
  (:import
   (java.util.regex Pattern)))

(defn- libspec-whitelist* []
  (let [kondo-file (io/file ".clj-kondo" "config.edn")
        exclude (when (.exists kondo-file)
                  (try
                    (-> kondo-file slurp read-string :linters :unused-namespace :exclude)
                    (catch Exception e
                      (when (System/getenv "CI")
                        (throw e)))))]
    (->> exclude
         (mapv (fn [entry]
                 (re-pattern (str "^" (Pattern/quote (str entry)) "$"))))
         (into (:libspec-whitelist config/*config*)))))

(def ^:private ^:dynamic *libspec-whitelist* nil)

(defn with-memoized-libspec-whitelist* [f]
  (binding [*libspec-whitelist* (memoize libspec-whitelist*)]
    (f)))

(defn libspec-whitelist
  "Obtains a libspec whitelist, which is the result of merging clj-refactor's own `:libspec-whitelist`
  with clj-kondo's `:unresolved-namespace` config.

  Uses a memoized version if available."
  []
  (or *libspec-whitelist*
      (libspec-whitelist*)))

(defmacro with-memoized-libspec-whitelist
  "Memoizes the libspec-whitelist internals while `body` is executing.

  _Temporary_ memoization is important because:

  * one does want to reload clj-kondo config if the user changes it, without needing a JVM restart;
  * one doesn't want that to imply a performance hit in terms of repeatedly reading clj-kondo config files."
  {:style/indent 0}
  [& body]
  `(with-memoized-libspec-whitelist*
     (fn []
       ~@body)))
