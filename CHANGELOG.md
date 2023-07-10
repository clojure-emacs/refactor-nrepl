# Changelog

## Unreleased

* Upgrade various dependencies.
  * Does not impact users, since we use [mranderson](https://github.com/benedekfazekas/mranderson).

## 3.7.1

* Fix an oversight in `suggest-libspecs`.

## 3.7.0

* Implement new middleware op: `suggest-libspecs`
  * Supports a beta clj-refactor.el feature.

## 3.6.0

* [#387](https://github.com/clojure-emacs/refactor-nrepl/issues/387): extend clj-kondo `:unused-namespace` integration. Now namespace local configuration is also taken into account.
  `:libspec-whitelist` can be augmented for particular namespace by:
  * Adding `:unused-namespace` linter configuration under `:config-in-ns` key in clj-kondo's config file. Like so: `:config-in-ns {example.target.ns {:linters {:unused-namespace {:exclude [ns.to.exclude]}}}}`
  * Adding `:unused-namespace` linter configuration under `:clj-kondo/config` key in metadata or attribute map of namespace. Like so: `(ns example.target.ns {:clj-kondo/config '{:linters {:unused-namespace {:exclude [ns.to.exclude]}}}})`

## 3.5.5

* [#385](https://github.com/clojure-emacs/refactor-nrepl/pull/385): only `suggest-aliases` that are valid symbols. 
  * Fixes an edge case for cljr-refactor.el's `cljr-slash`.

## 3.5.4

* [#383](https://github.com/clojure-emacs/refactor-nrepl/issues/383): `prune-dependencies`: increase accuracy for `:cljs`.

## 3.5.3

* [#382](https://github.com/clojure-emacs/refactor-nrepl/issues/382): `refactor-nrepl.artifacts`: increase resiliency.

## 3.5.2

* [#378](https://github.com/clojure-emacs/refactor-nrepl/issues/378): Fix `:as-alias` handling for .cljc files.

## 3.5.1

* Refine `:as-alias` detection.

## 3.5.0

* [#374](https://github.com/clojure-emacs/refactor-nrepl/issues/374): support Clojure 1.11's new `:as-alias` namespace directive. 

## 3.4.2

* [#373](https://github.com/clojure-emacs/refactor-nrepl/issues/373): revert accidentally-changed `namespace-aliases` error handling default.

## 3.4.1

* Offer `refactor-nrepl.ns.libspecs/namespace-aliases-for` function.
  * It's basically like `namespace-aliases`, but accepts files rather than dirs as an argument, which can be more flexible for programmatic use.

## 3.4.0

* [#369](https://github.com/clojure-emacs/refactor-nrepl/issues/369): Implement "suggest" option for the `namespace-aliases` op.
  * This allows end-users to type [Stuart Sierra style](https://stuartsierra.com/2015/05/10/clojure-namespace-aliases) aliases and have them completed, even if this alias wasn't in use anywhere in a given codebase.

## 3.3.2

* [#173](https://github.com/clojure-emacs/refactor-nrepl/issues/173): `rename-file-or-dir`: rename more kinds of constructs in dependent namespaces: namespace-qualified maps, fully-qualified functions, metadata.
* [#194](https://github.com/clojure-emacs/refactor-nrepl/issues/194): Don't prune `require` forms if they are needed for a given `import` to work.
* [#142](https://github.com/clojure-emacs/refactor-nrepl/issues/142): `read-ns-form`: report more informatively when a non-existing file is being processed.

## 3.3.1

* [#363](https://github.com/clojure-emacs/refactor-nrepl/issues/363): Fix a memoization bug in `clean-namespace`.

## 3.3.0

* [#361](https://github.com/clojure-emacs/refactor-nrepl/pull/361) Honor clj-kondo `:unused-namespace` config, if present
  * This piece of config can inform/complement refactor-nrepl's own config.
  * If you are using refactor-nrepl programatically (as opposed to as nREPL middleware), you can improve performance by using `refactor-nrepl.ns.libspec-allowlist/with-memoized-libspec-allowlist` prior to invoking `clean-ns`.

## 3.2.2 (2022-01-29)

* Fix a minor artifact in the previous release (the version would be reported as 0.0.0).

## 3.2.1 (2022-01-29)

* Upgrade Orchard.

## 3.2.0 (2022-01-09)

### Changes

* Address minor warnings that could be seen under the Clojure 1.11 series.
  * Accomplished by upgrading Orchard, and `tools.analyzer.jvm`.
* [#355](https://github.com/clojure-emacs/refactor-nrepl/issues/355): Disable the side-effects (as protocol extensions) performed by the `fs` library.

## 3.1.0 (2021-11-09)

### Changes

* [#344](https://github.com/clojure-emacs/refactor-nrepl/issues/344): make clean-ns's style closer to the [how to ns](https://stuartsierra.com/2016/08/27/how-to-ns) style.
* [#333](https://github.com/clojure-emacs/refactor-nrepl/issues/333): skip scanning irrelevant directories in more places (as it was already done for various other functionalities; this limits ananlysis/refactoring to your source/test paths, skipping other artifacts).
* Make `resolve-missing` able to find even more classes than before. 
* [#346](https://github.com/clojure-emacs/refactor-nrepl/issues/346): refine the heuristic for ignoring irrelevant dirs (see the above bullet point).
* Introduce `print-right-margin`/`print-miser-width` configuration options, used during `pprint`ing of ns forms.
  * The default is one that is consistent with refactor-nrepl's traditional behavior.
  * You can set both to `nil` for disabling line wrapping. 
* ns form printing: also wrap single-segment namespaces in a vector.

## 3.0.0 (2021-10-25)

### Changes

* [(Part of #230)](https://github.com/clojure-emacs/refactor-nrepl/issues/230): Parallelize various functionality
  * This will have a noticeable improvement in e.g. clj-refactor.el's `cljr-slash` performance.
* [#291](https://github.com/clojure-emacs/refactor-nrepl/issues/291): The `:ignore-errors` option will be honored in more places, making refactor-nrepl more robust in face of files not particularly meant to be part of the AST corpus.
  * Examples: WIP files, Moustache template files, scripts.
* Upgrade Orchard
  * Worth emphasizing: now the following options are available https://github.com/clojure-emacs/orchard/tree/v0.7.0#configuration-options
  * They can make the refactor-nrepl experience simpler / more robust.
* Upgrade rewrite-clj
  * This fixes some features such as `rename-file-or-dir`.
* Reliability improvement: try using `require` prior to `find-ns`
  * This increases the chances that a namespace will be found, which in turns makes refactor-nrepl more complete/accurate.
* [#306](https://github.com/clojure-emacs/refactor-nrepl/issues/306): Replace Cheshire with `clojure.data.json`.
* Build ASTs more robustly (by using locks, `require`, and ruling out certain namespaces like refactor-nrepl itself)
* Improve `namespace-aliases` performance and make it return more accurate results.
* Honor internal `future-cancel` calls, improving overall responsiveness and stability.

### Bugs fixed

* [#335](https://github.com/clojure-emacs/refactor-nrepl/issues/335): Strengthen `resolve-missing` against various edge cases.
* [#289](https://github.com/clojure-emacs/refactor-nrepl/issues/289): Fix an edge-case with involving keywords that caused find-symbol to crash.
* [#305](https://github.com/clojure-emacs/refactor-nrepl/issues/305): Don't put `:as` or `:refer` on their own lines in the ns form, when the libspec is so long it causes the line to wrap.
* [clojure-emacs/clj-refactor.el#459](https://github.com/clojure-emacs/clj-refactor.el/issues/459): `clean-ns` should conform to the style guide: `(:require` in the ns form should be followed by a newline.
  * You can opt out via the new `:insert-newline-after-require` configuration option.
* [#294](https://github.com/clojure-emacs/refactor-nrepl/pull/294): Properly skip uneval nodes when looking for the first/last sexp
* From now on, if you set the `clojure.tools.namespace.repl/refresh-dirs`, files outside said `refresh-dirs` won't be analyzed, resulting in safer, more efficient analysis.

## 2.5.1 (2021-02-16)

### Bugs fixed

* [#284](https://github.com/clojure-emacs/refactor-nrepl/issues/284): Don't truncate the artifacts cache.

### Changes

* Updated all deps.

## 2.5.0 (2020-02-29)

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

## 2.4.0 (2018-08-26)

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
