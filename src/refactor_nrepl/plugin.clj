(ns refactor-nrepl.plugin)

(defn middleware [project]
  (-> project
      (update-in [:dependencies]
                 (fnil into [])
                 [['refactor-nrepl "0.1.0-SNAPSHOT"]])
      (update-in [:repl-options :nrepl-middleware]
                 (fnil into [])
                 '[refactor-nrepl.refactor/wrap-refactor])))
