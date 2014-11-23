[![Build Status](https://travis-ci.org/clojure-emacs/refactor-nrepl.png?branch=master)](https://travis-ci.org/clojure-emacs/refactor-nrepl)

# Refactor nREPL

nREPL middleware to support refactorings in an editor agnostic way.

The role of this nREPL middleware is to provide refactoring support for clients such as [clj-refactor.el](https://github.com/clojure-emacs/clj-refactor.el).  As such, this middleware doesn't perform any refactorings, but returns the information about what needs doing to the client.

## Usage

### Adding the middleware via Leiningen

Add the following, either in your project's `project.clj`,  or in the `:user` profile found at `~/.lein/profiles.clj`:

```clojure
:plugins [[refactor-nrepl "0.2.0"]]
```

### Clojure client

A clojure client is provided for demonstrative purposes, and to make some refactorings available from the REPL.

The refactoring functions are provided as public functions of the `refactor-nrepl.client` namespace. To work with these functions, you need to connect to a nREPL server which has the `refactor-nrepl` middleware enalbed.

To connect you have two options:

1. Call `connect`, store the returned `transport` and pass this to all the refactor functions.
2. Call the refactor functions without a transport, in which case the client will create and store its own transport.

You also need to pass in the path to the file you want to refactor. Pass in a path as you would provide it for `slurp`. You might also need to pass in more optional or required arguments depending on the the refactor function -- see the documenation of the refactor functions in the `refactor-nrepl.client` namespace.

## Available features

### Find (debug) function invocations

Searches for invocations of predefined list of functions.

Expected input:
- ns-string -- the body of the namespace to work with
- refactor-fn -- value: "find-debug-fns"
- debug-fns -- coma separated list of functions to find invocations of

Returns tuples containing [line-number end-line-number column-number end-column-number fn-name].

The functions to look for should be listed in a coma separated string either fully quailified or just the function name. If the fully qualified name is provided the middleware will find the invocations of the function if it is required plainly or with an alias. For example:

```clojure
(:require [clojure.set]
          [secret-santa.util :as u])
```

However, if only the function name is provided the middleware will find invocations of the function where it is referred. For Example

```clojure
(:require [clojure.set :refer [difference]]
          [secret-santa.util :refer :all])
```

In case your client want to find core clojure functions (for example `println`) list them only with the function name. A list of functions names to find: `"println,pr,prn,secret-santa.util2/foobar,print-let,print.foo/print-let"`.

 It is the responsibility of the client what to do with the returned information: remove these functions, highlight them etc.

Example call from the repl using the clojure client:

```clojure
(require 'refactor-nrepl.client)
(def tr (refactor-nrepl.client/connect))
(refactor-nrepl.client/remove-debug-invocations :transport tr :file "src/secret_santa/core.clj")
```

### Find Referred

This is only available as a temporary performance tweak for [clj-refactor.el/remove-requires](https://github.com/clojure-emacs/clj-refactor.el#usage). Remove unusued requires is planned to be migrated as a whole (and hopefully improved too) in the near feature to be supported by the middleware. This feature might not be supported anymore by then.

### Artifact lookup

This middleware provides operations for obtaining information about artifacts from clojars, or mvn central.

Two ops are available:

#### artifact-list

Takes no arguments and returns a space-separated list of all available artifacts.

#### artifact-versions

Takes one required argument, `artifact` which is the full name of the artifact e.g. `core.clojure/clojure`, and one optional argument `force` which indicates whether we should force an update of the cached artifacts.

The return value is a space-separated list of all the available versions for the artifact.

### find symbols

Finds occurrences of symbols like defs and defns both where they are defined (if available) and where they are used.

#### find usages (application of find symbols)

Finds occurrences of symbols like defs and defns both where they are defined (if available) and where they are used and prints them.

Example call from the repl:
```clojure
(refactor-nrepl.client/find-symbol :ns 'leiningen.gargamel :name "gargamel-changelog")
```

#### rename symbols (application of find symbols)

Finds and renames occurrences of symbols like defs and defns both where they are defined -- if makes sense -- and where they are used. Uses the same backend function: find symbols; replacing the occurrences is implemented in the client.

```clojure
(refactor-nrepl.client/rename-symbol :ns 'leiningen.gargamel :name "gargamel-changelog" :new-name "garg-cl")
```

## Changelog

* find usages
* rename symbols

### 0.1.0

* find (debug) invocations
* find referred
* artifact lookup

## License

Copyright © 2013-2014 Benedek Fazekas, Magnar Sveen, Alex Baranosky, Lars Andersen

Distributed under the Eclipse Public License, the same as Clojure.
