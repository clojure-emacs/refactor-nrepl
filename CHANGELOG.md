# Changelog

## Unreleased

* Performance: `find-symbol`/`find-usages` no longer build an AST (which requires loading and evaluating the namespace) for files whose source doesn't even mention the symbol. Since only occurrences present before macroexpansion are ever reported, a textual pre-check safely skips the expensive analysis for the vast majority of files, turning "analyze the whole project" into "analyze the few files that reference the symbol". This also improves reliability, as far fewer namespaces get loaded.

