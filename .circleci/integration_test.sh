#!/usr/bin/env bash
set -Eeuxo pipefail

make install

cd .circleci/integration-testing/foo

lein with-profile -user repl <<< "(@(requiring-resolve 'refactor-nrepl.ns.libspecs/namespace-aliases))" | grep --silent --fixed-strings "{:clj {other (foo.other)}, :cljs {}"
