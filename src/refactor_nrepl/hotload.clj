(ns refactor-nrepl.hotload
  "Runtime addition of Maven dependencies via `clojure.tools.deps`.

  Designed to work both under Leiningen and the Clojure CLI: we resolve
  coordinates through `tools.deps` directly rather than via the basis
  machinery (which is only populated by the CLI launcher)."
  (:require
   [clojure.java.io :as io]
   [clojure.tools.deps :as deps]
   [clojure.tools.deps.util.maven :as maven])
  (:import
   (clojure.lang DynamicClassLoader RT)))

;; Libs already added in this JVM, keyed by symbol. Mirrors what
;; `clojure.tools.deps/resolve-added-libs` expects as `:existing` so repeat
;; loads no-op.
(defonce ^:private added-libs (atom {}))

(defn- find-dynamic-classloader
  "Returns the topmost `DynamicClassLoader` in the ancestor chain of the
  classloader that `require`/`load` currently uses (i.e. `RT.baseLoader()`),
  or nil if none is present. Added URLs will be visible to subsequent
  `require`/`load` calls on this thread."
  ^DynamicClassLoader []
  (loop [cl (RT/baseLoader)
         found nil]
    (cond
      (nil? cl) found
      (instance? DynamicClassLoader cl) (recur (.getParent cl) cl)
      :else (recur (.getParent cl) found))))

(defn add-libs!
  "Resolves `lib-coords` (a deps.edn-style map of `lib -> coord`) and adds
  the resulting JARs to the current `DynamicClassLoader`.

  Returns the map of freshly added libs (lib -> resolved coord, with
  `:paths`). Libs already on the classpath are skipped.

  Throws `IllegalStateException` if no `DynamicClassLoader` is reachable
  from the current thread."
  [lib-coords]
  (let [cl (find-dynamic-classloader)]
    (when-not cl
      (throw (IllegalStateException.
              "Hotloading requires a DynamicClassLoader in the thread context; none found.")))
    (let [{:keys [added]} (deps/resolve-added-libs
                           {:existing @added-libs
                            :add lib-coords
                            :procurer {:mvn/repos maven/standard-repos}})]
      (doseq [coord (vals added)
              path (:paths coord)]
        (.addURL cl (-> ^String path io/file .toURI .toURL)))
      (swap! added-libs merge added)
      added)))
