(ns maybe-deploy
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]))

(def release-marker "v")

(defn make-version [tag]
  (str/replace-first tag release-marker ""))

(defn log-result [m]
  (println m)
  m)

(defn -main [& _]
  (let [tag (System/getenv "CIRCLE_TAG")]
    (if-not tag
      (System/exit 1)
      (if-not (re-find (re-pattern release-marker) tag)
        (System/exit 1)
        (let [version (make-version tag)]
          (spit (io/file "resources" "refactor_nrepl" "version.edn")
                (pr-str version))
          (apply println "Executing" *command-line-args*)
          (->> [:env (into {"PROJECT_VERSION" version} (System/getenv))]
               (into (vec *command-line-args*))
               (apply sh)
               log-result
               :exit
               (System/exit)))))))
