# Changelog

## Unreleased

* [#458](https://github.com/clojure-emacs/refactor-nrepl/pull/458): Describe what every op requires, accepts and returns in the middleware descriptor, so that nREPL's `describe` op reports it.
* [#456](https://github.com/clojure-emacs/refactor-nrepl/pull/456): Fix `artifact-list` missing most of the `org.clojure` and `com.cognitect` artifacts, and stalling for 30 seconds, because it asked Maven Central for more results than its search API serves.
* [#456](https://github.com/clojure-emacs/refactor-nrepl/pull/456): Fix `artifact-versions` leaving out the older versions of an artifact that has more than 100 of them on Maven Central.
* [#455](https://github.com/clojure-emacs/refactor-nrepl/pull/455): Run the long-running ops (`find-symbol` and the like) on the session's thread, so that nREPL's `interrupt` op can stop them.
* [#454](https://github.com/clojure-emacs/refactor-nrepl/pull/454): Make `find-symbol` list the files it skipped because of `ignore-errors` under `skipped` in its final message.

## 3.16.0 (2026-11-12)

* [#453](https://github.com/clojure-emacs/refactor-nrepl/pull/453): Fix `find-symbol` ignoring the request's `ignore-paths` and `debug` options.
* [#452](https://github.com/clojure-emacs/refactor-nrepl/pull/452): Fix `find-symbol` leaving the client without a response when the search fails (e.g. the namespace can't be analyzed).
* [#452](https://github.com/clojure-emacs/refactor-nrepl/pull/452): Make the "unable to build an AST" error name the root cause's class and its line/column, and suggest reloading the namespace.

## 3.15.0 (2026-10-28)

* [#451](https://github.com/clojure-emacs/refactor-nrepl/pull/451): Deprecate `resolve-missing`, `clean-ns`, `namespace-aliases` and `cljr-suggest-libspecs`, which cider-nrepl's `cider/resolve-missing`, `cider/clean-ns` and `cider/resolve-alias` replace. They'll be removed in 3.17.0.

## 3.14.0 (2026-07-01)

* Performance: `find-symbol`/`find-usages` no longer build an AST (which requires loading and evaluating the namespace) for files whose source doesn't even mention the symbol. Since only occurrences present before macroexpansion are ever reported, a textual pre-check safely skips the expensive analysis for the vast majority of files, turning "analyze the whole project" into "analyze the few files that reference the symbol". This also improves reliability, as far fewer namespaces get loaded.
* Performance: `find-macros` skips reader-parsing files that don't textually contain `defmacro`, and defers building the project-wide namespace tracker until the queried symbol is confirmed to be a project macro (previously the tracker was built on every `find-symbol` call, even for non-macros).
* Reliability: the AST cache is now bounded (LRU) to avoid `OutOfMemoryError` on large projects. The limit defaults to 512 namespaces and is tunable via `refactor-nrepl.analyzer/ast-cache-limit` (`reset!` it, or set it to `nil` to disable the bound). Eviction only costs a rebuild on the next access, never correctness.
* Reliability: building the AST for a namespace that does `(set! *warn-on-reflection* true)` (or `*unchecked-math*`) at the top level no longer fails with "Can't change/establish root binding ... with set". Those vars are now thread-bound around analysis, which `analyze-ns` evaluates. Previously this only worked by accident, when the namespace happened to be analyzed and cached by an earlier, unrelated operation.

