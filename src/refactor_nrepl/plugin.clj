(ns refactor-nrepl.plugin
  (:require [clojure.java.io :as io]))

(defn- version []
  (let [v (-> (io/resource "refactor-nrepl/refactor-nrepl/project.clj")
              slurp
              read-string
              (nth 2))]
    (assert (string? v)
            (str "Something went wrong, version is not a string: "
                 v))
    v))

(defn middleware [project]
  (-> project
      (update-in [:dependencies]
                 (fnil into [])
                 [['refactor-nrepl (version)]])
      (update-in [:repl-options :nrepl-middleware]
                 (fnil into [])
                 '[refactor-nrepl.find-symbol/wrap-find-symbol
                   refactor-nrepl.ns.clean-ns/wrap-clean-ns
                   refactor-nrepl.ns.resolve-missing/wrap-resolve-missing
                   refactor-nrepl.find-unbound/wrap-find-unbound
                   refactor-nrepl.artifacts/wrap-artifacts])))
