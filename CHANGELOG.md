# Changelog

## Unreleased

* Performance: `find-symbol`/`find-usages` no longer build an AST (which requires loading and evaluating the namespace) for files whose source doesn't even mention the symbol. Since only occurrences present before macroexpansion are ever reported, a textual pre-check safely skips the expensive analysis for the vast majority of files, turning "analyze the whole project" into "analyze the few files that reference the symbol". This also improves reliability, as far fewer namespaces get loaded.
* Performance: `find-macros` skips reader-parsing files that don't textually contain `defmacro`, and defers building the project-wide namespace tracker until the queried symbol is confirmed to be a project macro (previously the tracker was built on every `find-symbol` call, even for non-macros).
* Reliability: the AST cache is now bounded (LRU) to avoid `OutOfMemoryError` on large projects. The limit defaults to 512 namespaces and is tunable via `refactor-nrepl.analyzer/ast-cache-limit` (`reset!` it, or set it to `nil` to disable the bound). Eviction only costs a rebuild on the next access, never correctness.
* Reliability: building the AST for a namespace that does `(set! *warn-on-reflection* true)` (or `*unchecked-math*`) at the top level no longer fails with "Can't change/establish root binding ... with set". Those vars are now thread-bound around analysis, which `analyze-ns` evaluates. Previously this only worked by accident, when the namespace happened to be analyzed and cached by an earlier, unrelated operation.

