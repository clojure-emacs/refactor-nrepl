# Changelog

## Unreleased

### New features

* [clojure-emacs/clj-refactor.el#443](https://github.com/clojure-emacs/clj-refactor.el/issues/443): `clean-ns` support namespaces with js literal (#js).
* [#251](https://github.com/clojure-emacs/refactor-nrepl/pull/251): `clean-ns` support extra message key `relative-path`, which will be used if `path` does not exist.
* [#264](https://github.com/clojure-emacs/refactor-nrepl/pull/264): Less bandwidth used to fetch artifacts from Clojars.
* [#268](https://github.com/clojure-emacs/refactor-nrepl/pull/268): Added support for fetching artifact versions from Clojars.
* [#271](https://github.com/clojure-emacs/refactor-nrepl/pull/271): Improved shadow-cljs support.

### Changes

* Drop support for nREPL 0.2.

### Bugs fixed

* [#256](https://github.com/clojure-emacs/refactor-nrepl/pull/256): Ignore malformed artifact coordinates when fetching from Clojars.

## 2.4.0

### New features

* [#215](https://github.com/clojure-emacs/refactor-nrepl/issues/215): Support JVM system proxy in mvn artifacts listing.

### Changes

* [#231](https://github.com/clojure-emacs/refactor-nrepl/issues/231): Hotload-dependencies temporarily disabled due to java 10 issues.
* [#198](https://github.com/clojure-emacs/refactor-nrepl/issues/198): Delay middleware loading to speed up initialization.
* Drop Clojure 1.7.0 support.

### Bugs fixed

* Bump [mranderson](https://github.com/benedekfazekas/mranderson): Version to fix leiningen 2.8.x incompatibility issues.

## 2.3.1

### Bugs fixed

* [#187](https://github.com/clojure-emacs/refactor-nrepl/issues/187) Update clojure.tools.reader so Clojure 1.9 namespaced map literals don't cause parse errors.
* [#185](https://github.com/clojure-emacs/refactor-nrepl/issues/185) Report throwables of type `Error` instead of swallowing them.
* [#186](https://github.com/clojure-emacs/refactor-nrepl/issues/186) Make sure `resolve-missing` still works, even if a candidate class has missing dependencies.
* [#184](https://github.com/clojure-emacs/refactor-nrepl/pull/184) In `resolve-missing`, prevent classpaths with many entries from causing a stack overflow.
* [clojure-emacs/clj-refactor.el#330](https://github.com/clojure-emacs/clj-refactor.el/issues/332) `clean-ns` removes imported inner inner classes.
* [clojure-emacs/clj-refactor.el#330](https://github.com/clojure-emacs/clj-refactor.el/issues/330) `clean-ns` ignores namespaced keywords.
* [#160](https://github.com/clojure-emacs/refactor-nrepl/issues/160) Make `resolve-missing` find newly defined vars and types (clj). Because of a stale cache, newly added vars or types would not be found. This fix takes into account vars/types added by eval-ing code (rescan affected namespace), and by hotloading dependencies (reset the cache).
* [clojure-emacs/clj-refactor.el#362](https://github.com/clojure-emacs/clj-refactor.el/issues/362) Preserve all shorthand style metadata when `clean-ns` is used.
* [#192](https://github.com/clojure-emacs/refactor-nrepl/issues/192) Clean ns understands `$` as a symbol. Specially this enables clean ns to work with incanter that does have function named `$`.
* [clojure-emacs/clj-refactor.el#332](https://github.com/clojure-emacs/clj-refactor.el/issues/332) Fix regression when inner class needs to be imported, strangely implementation of multiple levels of nested inner classes broke the simpler case.

### Changes

* [#169](https://github.com/clojure-emacs/refactor-nrepl/issues/169) Fix java9 compatibility issues.

### New features

* New config setting `:libspec-whitelist` which makes it possible to create a seq of namespaces `clean-ns` shouldn't prune.  This is useful for libspecs which aren't used except through side-effecting loads.
* New config setting `:ignore-paths` for ignoring certain paths when finding dirs on classpath.

## 2.2.0

### New features

* Add `find-used-publics` which list occurrences of symbols defined in namespace A in namespace B.
* [#145](https://github.com/clojure-emacs/refactor-nrepl/issues/145) Enable caching for resolve missing
* Add support for skipping excluded dependencies. This patch tweaks the dependency checker, so that refactor-nrepl will activate itself normally if one of its dependencies is listed in a project's :exclusions. This enables a user to for instance exclude org.clojure/clojure and provide a fork, thus taking on all responsibility for providing an adequate version.


### Bugs fixed

* Fix resolve-missing for cljs. Before this fix, the cljs-path returned only a single candidate. That's because every candidate was merged together. We now use `merge-with into` to generate a list of candidates instead.
* [#147](https://github.com/clojure-emacs/refactor-nrepl/issues/147) Avoid needless .cljc namespace reader macro usage

### Changes

* [#133](https://github.com/clojure-emacs/refactor-nrepl/issues/133) Filter out clojure source files without ns form when indexing/analyzing so projects whith such files are supported.


## 2.0.0

### New features

* Add support for `:rename` clauses in clean ns.
* Add cljc support to `clean-ns`.
* Add cljs support to `clean-ns`.
* `rename-file-or-dir` now knows how to move cljs files.
* The `artifact-version` list is now sorted.
* Add `namespace-aliases` which provides a mapping of the namespace aliases that are in use in the project.
* Make `find-symbol` able to handle macros.

### Bugs fixed

* [clojure-emacs/clj-refactor.el#223](https://github.com/clojure-emacs/clj-refactor.el/issues/223) Fix clean-ns removing metadata when file starts with a comment.
* [#103](https://github.com/clojure-emacs/refactor-nrepl/issues/108) `find-symbol` failes in projects with `cljc` files.
* [#106](https://github.com/clojure-emacs/refactor-nrepl/issues/106) Failure to build ASTs when namespaced keywords are present in the project.
* [#127](https://github.com/clojure-emacs/refactor-nrepl/issues/127) tools.namespace's tracker doesn't handle namespaces of same name across dialects. Temporary fix, `:require-macros` dependencies not tracked across platforms, also see [TNS-38](http://dev.clojure.org/jira/browse/TNS-38)
* [#91](https://github.com/clojure-emacs/refactor-nrepl/issues/91) Find usages/Rename symbol gets confused after rename file or dir
* [#126](https://github.com/clojure-emacs/refactor-nrepl/issues/126) Build AST for nses in project in topological order, fixes problem when protocols got evaluated after dependent deftypes resulting in `No implementation of method` type errors.

### Changes

* [#132](https://github.com/clojure-emacs/refactor-nrepl/issues/132)
When the middleware is started with the wrong Clojure version we now
warn and exclude the middleware instead of failing catastrophically.
* `resolve-missing` now returns a map instead of an alist.
* [clojure-emacs/clj-refactor.el#275](https://github.com/clojure-emacs/clj-refactor.el/issues/275) clean-ns will no longer prune cljsjs requires.
* New option for `clean-ns`, `prune-ns-form`, to avoid pruning the ns-form.
* Get rid of the client namespace
* [#118](https://github.com/clojure-emacs/refactor-nrepl/issues/118) Improve the `find-symbol` reply.  It's now a map instead of a vector.
* Remove `find-debug-fns`.  None of us ever used this and there's some
  overlap with `find-usages`.
* Rename `find-unbound` to `find-used-locals`.  This is what this op has been doing for the last several versions.
* Drop the `configure` op, and receive settings in each message.
* More relaxed when building ASTs so some coding errors are tolerated and AST based features work with slightly broken code

## 1.1.0

* Add `rename-file-or-dir` which returns a file or a directory of clj files.
* Add `extract-definition` which returns enough information to the clien to afford inlining of defs defns and let-bound vars.
* Add `stubs-for-interface` for creating skeleton interface implementations
* Add `warm-ast-cache` op for eagerly building, and caching, ASTs of project files

## 1.0.5

* add 'version' op, which returns the current version of refactor-nrepl to the client.

## 1.0.4

* fix for clean-ns removing import only used in macro
* fix for clean-ns removes classes used only in typehints
* workaround for analyzer bug which results in wrong filename in var meta
* fix for find-unbound edge cases

## 1.0.3

* fix problem in clean-ns caused by mranderson (inlining) limitation

## 1.0.2 (this is broken version, please use 1.0.3!)

* various bugfixes in clean-ns
* improvements on error messages
* fix in find unbound in case of s-expression is inside a macro
* resolve missing works for static method, field and resolve missign will work right after hotload dependency

## 1.0.1

* throw an error for cljs and cljx file: they are not supported (yet)
* minor refactorings, clean ups
* readme tweaks
* fix for find-unbound does not always figure out the right parameters for new function

## 1.0.0

* Config setting for `clean-ns` to not do any prefix rewriting
* Add `configure` op to set various config opts.
* Remove find referred
* Add `hotload-dependency` which loads a new project dependency into the repl
* Add caching of ASTs for better performance
* Add `resolve-missing` which resolves a missing symbol by scanning the classpath
* Add `clean-ns` which performs various cleanups on the ns form.
* various cleaning and refactoring stuff

## 0.2.2

* AST creation: analyze-ns instead of plain analyze which also evals the code

## 0.2.1

* find usages
* rename symbols

## 0.1.0

* find (debug) invocations
* find referred
* artifact lookup
