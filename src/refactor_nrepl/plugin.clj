(ns refactor-nrepl.plugin
  (:require [clojure.java.io :as io]))

(defn middleware [project]
  (-> project
      (update-in [:dependencies]
                 (fnil into [])
                 ;; temporary: should be solved properly if we migrate to boot
                 [['refactor-nrepl "0.3.0-BOOT"]])
      (update-in [:repl-options :nrepl-middleware]
                 (fnil into [])
                 '[refactor-nrepl.refactor/wrap-refactor
                   refactor-nrepl.ns.clean-ns/wrap-clean-ns
                   refactor-nrepl.ns.resolve-missing/wrap-resolve-missing
                   refactor-nrepl.find-unbound/wrap-find-unbound
                   refactor-nrepl.artifacts/wrap-artifacts])))
