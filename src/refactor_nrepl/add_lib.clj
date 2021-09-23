;;   Copyright (c) Rich Hickey. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns refactor-nrepl.add-lib
  (:require
   [clojure.java.io :as jio]
   [clojure.set :as set]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.util.maven :as maven])
  (:import
   clojure.lang.DynamicClassLoader
   java.io.File))

(set! *warn-on-reflection* true)

;; maintain basis

(defn- read-basis
  []
  (when-let [f (jio/file (System/getProperty "clojure.basis"))]
    (if (and f (.exists f))
      (deps/slurp-deps f)
      (throw (IllegalArgumentException. "No basis declared in clojure.basis system property")))))

(defonce ^:private init-basis (delay (read-basis)))

(defn launch-basis
  "Initial runtime basis at launch"
  []
  @init-basis)

(def ^:private runtime-basis
  (atom nil))

(defn- reset-basis
  [basis]
  (reset! runtime-basis basis))

(defn current-basis
  "Return the current runtime basis, which may have been modified since the launch."
  []
  (or @runtime-basis (reset-basis @init-basis)))

;; add-libs

(defn- add-loader-url
  "Add url string or URL to the highest level DynamicClassLoader url set."
  [url]
  (let [u (if (string? url) (java.net.URL. url) url)
        loader (loop [loader (.getContextClassLoader (Thread/currentThread))]
                 (let [parent (.getParent loader)]
                   (if (instance? DynamicClassLoader parent)
                     (recur parent)
                     loader)))]
    (if (instance? DynamicClassLoader loader)
      (.addURL ^DynamicClassLoader loader u)
      (throw (IllegalAccessError. "Context classloader is not a DynamicClassLoader")))))

(defn add-libs
  "Add map of lib to coords to the current runtime environment. All transitive
  dependencies will also be considered (in the context of the current set
  of loaded dependencies) and new transitive dependencies will also be
  loaded. Returns seq of all added libs or nil if couldn't be loaded.
  Note that for successful use, you must be in a REPL environment where a
  valid parent DynamicClassLoader can be found in which to add the new lib
  urls.
  Example:
   (add-libs '{org.clojure/core.memoize {:mvn/version \"0.7.1\"}})"
  [lib-coords]
  (let [{:keys [libs] :as initial-basis} (current-basis)]
    (if (empty? (set/difference (-> lib-coords keys set) (-> libs keys set)))
      nil ;; already loaded
      (let [updated-deps (reduce-kv (fn [m k v] (assoc m k (dissoc v :dependents :paths))) lib-coords libs)
            updated-edn (merge (dissoc initial-basis :libs :classpath :deps) {:deps updated-deps})
            {updated-libs :libs :as updated-basis}
            (deps/calc-basis
             ;; No `:mvn/repos` are configured if Leiningen is in use so we have to add them here.
             (merge {:mvn/repos maven/standard-repos} updated-edn)
             (select-keys initial-basis [:resolve-args :cp-args]))
            new-libs (select-keys updated-libs (set/difference (set (keys updated-libs)) (set (keys libs))))
            paths (mapcat :paths (vals new-libs))
            urls (->> paths (map jio/file) (map #(.toURL ^File %)))]
        ;; TODO: multiple unsynchronized changes to runtime state - coordinate with lock?
        (run! add-loader-url urls)
        (reset-basis updated-basis)
        (keys new-libs)))))
