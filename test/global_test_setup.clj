(ns global-test-setup
  "This namespace's contents will be automatically loaded by `lein test` (or any test runner).")

(def set-refresh-dirs
  (try
    (require '[refactor-nrepl.inlined-deps.toolsnamespace.v1v1v0.clojure.tools.namespace.repl])
    @(resolve 'refactor-nrepl.inlined-deps.toolsnamespace.v1v1v0.clojure.tools.namespace.repl/set-refresh-dirs)
    (catch Exception _
      (require '[clojure.tools.namespace.repl])
      @(resolve 'clojure.tools.namespace.repl/set-refresh-dirs))))

(when (System/getenv "CI") ; don't alter local environments (which may also use clojure.tools.namespace.repl)
  ;; Intentionally exclude the `lein-plugin/` source path:
  (set-refresh-dirs "src" "test"))
