.PHONY: inline-deps test release deploy clean

VERSION ?= 1.10

.inline-deps:
	lein inline-deps
	touch .inline-deps

inline-deps: .inline-deps

test: .inline-deps
	lein with-profile +$(VERSION),+plugin.mranderson/config test

cljfmt:
	lein with-profile +$(VERSION),+cljfmt cljfmt check

# When releasing, the BUMP variable controls which field in the
# version string will be incremented in the *next* snapshot
# version. Typically this is either "major", "minor", or "patch".

BUMP ?= patch

release:
	lein with-profile +$(VERSION) release $(BUMP)

# Deploying requires the caller to set environment variables as
# specified in project.clj to provide a login and password to the
# artifact repository.

deploy: .inline-deps
	lein with-profile +$(VERSION),+plugin.mranderson/config deploy clojars

clean:
	lein clean
	rm -f .inline-deps
