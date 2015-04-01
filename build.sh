#!/bin/bash

# runs source-deps and tests and then provided lein target with mranderson

lein do clean, source-deps :prefix-exclusions "[\"classlojure\"]"
lein with-profile +plugin.mranderson/config test
lein with-profile plugin.mranderson/config "$@"
