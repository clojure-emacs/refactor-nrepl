(ns refactor-nrepl.plugin
  (:require
   [refactor-nrepl.core :as core]
   [leiningen.core.main :as lein]))

(def ^:private external-dependencies
  ;; For whatever reason it didn't work to look for cider-nrepl here.
  {'org.clojure/clojure "1.7.0"})

(defn- version-ok?
  [dependencies artifact version-string]
  (or (->> dependencies
           (some (fn [[id v]]
                   (and (= id artifact)
                        (lein/version-satisfies? v version-string)))))
      (lein/warn (str "Warning: refactor-nrepl requires " artifact " "
                      version-string " or greater."))))

(defn- external-dependencies-ok? [dependencies exclusions]
  (let [exclusions (set exclusions)]
    (reduce (fn [acc [artifact version-string]]
              (or (exclusions artifact)
                  (and (version-ok? dependencies artifact version-string) acc)))
            true
            external-dependencies)))

(defn middleware
  [{:keys [dependencies exclusions] :as project}]
  (if (external-dependencies-ok? dependencies exclusions)
    (-> project
        (update-in [:dependencies]
                   (fnil into [])
                   [['refactor-nrepl (core/version)]])
        (update-in [:repl-options :nrepl-middleware]
                   (fnil into [])
                   '[refactor-nrepl.middleware/wrap-refactor]))
    (do
      (lein/warn (str "Warning: refactor-nrepl middleware won't be "
                      "activated due to missing dependencies."))
      project)))
