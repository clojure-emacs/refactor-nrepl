(ns global-test-setup
  "This namespace's contents will be automatically loaded by `lein test` (or any test runner)."
  (:require
   [refactor-nrepl.inlined-deps.toolsnamespace.v1v1v0.clojure.tools.namespace.repl :refer [set-refresh-dirs]]))

(when (System/getenv "CI") ; don't alter local environments (which may also use clojure.tools.namespace.repl)
  ;; Intentionally exclude the `lein-plugin/` source path:
  (set-refresh-dirs "src" "test"))
