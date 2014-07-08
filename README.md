[![Build Status](https://travis-ci.org/clojure-emacs/refactor-nrepl.png?branch=master)](https://travis-ci.org/clojure-emacs/refactor-nrepl)

# Refactor nREPL

nREPL middleware to support refactorings in an editor agnostic way.

## Usage

This middleware is planned to support certain refactorings with doing the heavy lifting with the help of an AST built by the [analyzer](https://github.com/clojure/tools.analyzer). However, the middleware itself does not do the refactoring as changing files or buffers. It rather returns the information which a client -- most likely some integration layer with an editor -- can use to perform the refactoring in a few simple steps.

The above integration layer will be implemented by [clj-refactor.el](https://github.com/clojure-emacs/clj-refactor.el) for Emacs in emacs-lisp. I will also add a client implementation in clojure in this project for demonstrative reasons and also the enable users to carry out refactorings directly from a REPL if they prefer.

### Adding the middleware via Leiningen

Use the convenient plugin for defaults, either in your project's
`project.clj` file or in the `:user` profile in
`~/.lein/profiles.clj`.

```clojure
:plugins [[refactor-nrepl "0.1.0-SNAPSHOT"]]
```

A minimal `profiles.clj` for refactoring features would be:

```clojure
{:user {:plugins [[refactor-nrepl "0.1.0-SNAPSHOT"]]}}
```

Or (if you know what you're doing) add `refactor-nrepl` to your `:dev :dependencies` vector plus specific
middleware to `:nrepl-middleware` under `:repl-options`.

```clojure
:dependencies [[refactor-nrepl "0.1.0-SNAPSHOT"]]
:repl-options {:nrepl-middleware
                 [refactor-nrepl.refactor/wrap-refactor]}
```

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

### Find Referred

This is only available as a temporary performance tweak for [clj-refactor.el/remove-requires](https://github.com/clojure-emacs/clj-refactor.el#usage). Remove unusued requires is planned to be migrated as a whole (and hopefully improved too) in the near feature to be supported by the middleware. This feature might not be supported anymore by then.

## License

Copyright © 2013-2014 Benedek Fazekas, Magnar Sveen, Alex Baranosky, Lars Andersen

Distributed under the Eclipse Public License, the same as Clojure.
