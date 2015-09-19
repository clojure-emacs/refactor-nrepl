[![Build Status](https://travis-ci.org/clojure-emacs/refactor-nrepl.png?branch=master)](https://travis-ci.org/clojure-emacs/refactor-nrepl)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/clojure-emacs/refactor-nrepl?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

# Refactor nREPL

nREPL middleware to support refactorings in an editor agnostic way.

The role of this nREPL middleware is to provide refactoring support for clients such as [clj-refactor.el](https://github.com/clojure-emacs/clj-refactor.el).

## Usage

### Adding the middleware via Leiningen

Add the following, either in your project's `project.clj`,  or in the `:user` profile found at `~/.lein/profiles.clj`:

```clojure
:plugins [[refactor-nrepl "1.1.0"]
          [cider/cider-nrepl "0.9.1"]]
```

### Adding the middleware via Boot

Add the following in `~/.boot/profile.boot`:

```clojure
(require 'boot.repl)

(swap! boot.repl/*default-dependencies* conj
       '[refactor-nrepl "1.1.0"]
       '[cider/cider-nrepl "0.9.1"])

(swap! boot.repl/*default-middleware* conj
       'refactor-nrepl.middleware/wrap-refactor)
```

### Clojure client

A clojure client is provided for demonstrative purposes, and to make some refactorings available from the REPL.

The refactoring functions are provided as public functions of the `refactor-nrepl.client` namespace. To work with these functions, you need to connect to a nREPL server which has the `refactor-nrepl` middleware enabled.

To connect you have two options:

1. Call `connect`, store the returned `transport` and pass this to all the refactor functions.
2. Call the refactor functions without a transport, in which case the client will create and store its own transport.

You also need to pass in the path to the file you want to refactor. Pass in a path as you would provide it for `slurp`. You might also need to pass in more optional or required arguments depending on the the refactor function -- see the documenation of the refactor functions in the `refactor-nrepl.client` namespace.

## Available features

### Configuration

Configuration settings are passed along with each msg, currently the recognized options are as follows:

```clj
{
:prefix-rewriting true ; Should clean-ns favor prefix forms in the ns macro?
:debug false ; verbose setting for debugging.
}
```

Any configuration settings passed along with the message will replace the defaults above.

### Find (debug) function invocations

Searches for invocations of predefined list of functions.

Expected input:
- ns-string -- the body of the namespace to work with
- refactor-fn -- value: "find-debug-fns"
- debug-fns -- coma separated list of functions to find invocations of

Returns tuples containing [line-number end-line-number column-number end-column-number fn-name].

The functions to look for should be listed in a comma separated string, either fully qualified or just the function name. If the fully qualified name is provided the middleware will find the invocations of the function if it is required plainly or with an alias. For example:

```clojure
(:require [clojure.set]
          [secret-santa.util :as u])
```

However, if only the function name is provided the middleware will find invocations of the function where it is referred. For Example

```clojure
(:require [clojure.set :refer [difference]]
          [secret-santa.util :refer :all])
```

In case your client wants to find core clojure functions (for example `println`) list them only with the function name. A list of functions names to find: `"println,pr,prn,secret-santa.util2/foobar,print-let,print.foo/print-let"`.

Example call from the repl using the clojure client:

```clojure
(require 'refactor-nrepl.client)
(def tr (refactor-nrepl.client/connect))
(refactor-nrepl.client/remove-debug-invocations :transport tr :file "src/secret_santa/core.clj")
```

### Artifact lookup

This middleware provides operations for obtaining information about artifacts from clojars, or mvn central.

Two ops are available:

#### artifact-list

Takes no arguments and returns a list of all available artifacts.

#### artifact-versions

Takes one required argument, `artifact` which is the full name of the
artifact e.g. `org.clojure/clojure`, and one optional argument `force`
which optionally triggers a forced update of the cached artifacts.

The return value is a sorted list, in decreasing order of relevance, with all the available versions.

### find-symbol

This op finds occurrences of a single symbol.

`find-symbol` requires:

`file` The absolute path to the file containing the symbol to lookup.

`dir` Only files below this dir will be searched.

`ns` The ns where the symbol is defined.

`name` The name of the symbol

`line` The line number where the symbol occurrs, counting from 1.

`column` The column number where the symbol occurs, counting from 1.

`ignore-errors` [optional] if set find symbol carries on even if there is broken namespace which we can not build AST for

The return value is a stream of occurrences under the key `occurrence` which is an alist like this:

`(:line-beg 5 :line-end 5 :col-beg 19 :col-end 26 :name a-name :file \"/aboslute/path/to/file.clj\" :match (fn-name some args))`

When the final `occurrence` has been sent a final message is sent with `count`, indicating the total number of matches, and `status` `done`.

Clients are advised to set `ignore-errors` on only for find usages as the rest of the operations built on find-symbol supposed to modify the project as well therefore can be destructive if some namespaces can not be analyzed.

#### find usages (application of find symbols)

Finds occurrences of symbols like defs and defns both where they are defined (if available) and where they are used and prints them.

Example call from the repl:
```clojure
(refactor-nrepl.client/find-symbol :ns 'leiningen.gargamel :name "gargamel-changelog")
```

#### rename symbols (application of find symbols)

Finds and renames occurrences of symbols like defs and defns both where they are defined -- if it makes sense -- and where they are used. Uses the same backend function: find symbols. Replacing the occurrences is implemented in the client.

```clojure
(refactor-nrepl.client/rename-symbol :ns 'leiningen.gargamel :name "gargamel-changelog" :new-name "garg-cl")
```

### clean-ns

The `clean-ns` op will perform the following cleanups on an ns form:

* Eliminate :use clauses
* Sort required libraries, imports and vectors of referred symbols
* Rewrite to favor prefix form, e.g. [clojure [string test]] instead
  of two separate libspecs
* Raise errors if any inconsistencies are found (e.g. a libspec with more than
  one alias.
* Remove any unused namespaces, referred symbols or imported classes.
* Remove any duplication in the :require and :import form.

The `clean-ns` requires a `path` which is the absolute path to the file containing the `ns` to be operated upon.

The return value, `ns` is the entire `(ns ..)` form in prestine condition, or `nil` if nothing was done (so the client doesn't update the timestamp on files when nothing actually needs doing).

Pretty-printing the `(ns ..)` form is surprisingly difficult.  The current implementation just puts stuff on the right line and delegates the actual indentation to the client.

In the event of an error `clean-ns` will return `error` which is an error message intended for display to the user.

**Warning**: The `clean -ns` op dependes on `tools.analyzer` to determine which vars in a file are actually being used.  This means the code is evaluated and any top-level occurrences of `(launch-missiles)` should be avoided.

This op can be [configured](#configuration).

### resolve-missing

The goal of the op is to provide intelligent suggestions when the user wants to import or require the unresolvable symbol at point.

The op requires `symbol` which represents a name to look up on the classpath.

The return value `candidates` is an alist of `((candidate1 . type1)
(candidate2 . type2) ...)` where type is in `#{:type :class :ns
:macro}` so we can branch on the various ways to make the symbol
available.  `:type` means the symbol resolved to a var created by
`defrecord` or `deftype`, `:class` also includes interfaces.  `:macro`
is only used if the op is called in a cljs context.

### hotload-dependency

Loads a new project dependency into the currently active repl.

The op requires `coordinates` which is a leiningen style dependency.

The return value is a `status` of `done` and `dependency` which is the coordinate vector that was hotloaded, or `error` when something went wrong.

### find-used-locals

This op finds available and used local vars in a selected s-expression
in a ns on the classpath.  In `clj-refactor` we use this as the
underlying op for the `extract-function` refactoring.

This op requires `file` which is the path of the file to work on as
well as `line` and `column`. The enclosing s-expression will be used
to determine the available and used locals.

Both `line` and `column` start counting at 1.

Return values `status` of `done` and `used-locals` which is a list of
unbound vars, or `error` when something went wrong.

The returned symbols' order is based on the order of their occurrence in
the macro expanded s-expression (that means reversed order for
threading macros naturally -- compared to what you actually see).

### stubs-for-interface

`stubs-for-interface` takes a single input `interface` which is a fully qualified symbol which resolves to either an interface or protocol.

The return value is `edn` and looks like this:

```clj
user> (stubs-for-interface {:interface "java.lang.Iterable"})
({:parameter-list "[^java.util.function.Consumer arg0]", :name "forEach"}
 {:parameter-list "[]", :name "iterator"}
 {:parameter-list "[]", :name "spliterator"})
```

The intended use-case for `stubs-for-interface` is to provide enough info to create skeleton implementations when implementing e.g. an interface in a defrecord.

### extract-definition

`extract-definition` is based on `find-symbol` so it takes the same input values.  The return value, `definition` is a string of edn which looks like this:

```clj
{:definition {:line-beg 4
              :line-end 4
              :col-beg 9
              :col-end 21
              :name \"another-val\"
              :file \"core.clj\"
              :match \"(let [another-val 321]\"
              :definition \"321\"}
 :occurrences ({:match \"(println my-constant my-constant another-val)))\"
                :file \"core.clj\"
                :name \"another-val\"
                :col-end 50
                :col-beg 38
                :line-end 5
                :line-beg 5})}
```

The key `:definition` contains information about the defining form, so the client can delete it.

The key `:occurrences` is a seq of all occurrences of the symbol which need to be inlined.  This means the definition itself is excluded to avoid any special handling by the client.

### version

This op returns, `version`, which is the current version of this project.

### warm-ast-cache

Eagerly builds, and caches ASTs for all clojure files in the project.  Returns `status` `done` on success and stats for the ASTs built: a list of namespace names as the odd members of the list and either 'OK' as the even member or the error message generated when the given namespace was analyzed. For example

```clojure
'(com.foo "OK" com.bar "OK" com.baz '("error" "Could not resolve var: keyw"))
```

### rename-file-or-dir

The `rename-file-or-dir` op takes an `old-path` and a `new-path` which are absolute paths to a file or directory.

If `old-path` is a directory, all files, including any non-clj files, are moved to `new-path`.

The op returns `touched` which is a list of all files that were affected by the move, and needs to be visited by the client to indent the updated ns form while we await proper pretty printing support in the middleware.

This op can cause serious havoc if it crashes midway through the
refactoring.  I recommend not running it without first creating a
restore point in your version control system.

### namespace-aliases

Returns `namespace-aliases` which is a list of all the namespace aliases that are in use in the
project. The reply looks like this:

```clj
{:clj
 {t (clojure.test),
  set (clojure.set),
  tracker (refactor-nrepl.ns.tracker clojure.tools.namespace.track)},
 :cljs {set (clojure.set), pprint (cljs.pprint)}}
```
The list of suggestions is sorted by frequency in decreasing order, so the first element is always the best suggestion.

### clojure-version

The `clojure-version` returns the clojure version running in the client as a string. e.g. `1.7.0-alpha1` or `1.6.1`.

### Errors

The middleware returns errors under one of two keys: `:error` or
`:err`.  The key `:error` contains an error string which is intended
for the end user.  The key `:err` is used for unexpected failures and
contains among other things a full stacktrace.

## Development with `mranderson`

[mranderson](https://github.com/benedekfazekas/mranderson) is used to avoid classpath collisions.

To work with `mranderson` the first thing to do is:

`lein do clean, source-deps :prefix-exclusions "[\"classlojure\"]"`

this creates the munged local dependencies in target/srcdeps directory

after that you can run your tests or your repl with:

`lein with-profile +plugin.mranderson/config repl`

`lein with-profile +plugin.mranderson/config test`

note the plus sign before the leiningen profile.

If you want to use `mranderson` while developing locally with the repl the source has to be modified in the `target/srcdeps` directory.

When you want to release
locally:

`lein with-profile plugin.mranderson/config install`

to clojars:

`lein with-profile plugin.mranderson/config deploy clojars`

Or alternatively run

`./build.sh install`

`./build.sh deploy clojars`

build.sh cleans, runs source-deps with the right parameters, runs the tests and then runs the provided lein target.

## Changelog

An extensive changelog is available [here](CHANGELOG.md).

## License

Copyright © 2013-2015 Benedek Fazekas, Magnar Sveen, Alex Baranosky, Lars Andersen

Distributed under the Eclipse Public License, the same as Clojure.
