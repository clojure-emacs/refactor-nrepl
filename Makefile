.PHONY: inline-deps test release deploy clean

VERSION ?= 1.10

clean:
	lein clean
	rm -f .inline-deps

.inline-deps: clean
	lein with-profile -user,+$(VERSION) inline-deps
	touch .inline-deps

inline-deps: .inline-deps

fast-test: clean
	lein with-profile -user,+$(VERSION) test

test: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config test

cljfmt:
	lein with-profile -user,+$(VERSION),+cljfmt cljfmt check

cljfmt-fix:
	lein with-profile -user,+$(VERSION),+cljfmt cljfmt fix

eastwood:
	lein with-profile -user,+$(VERSION),+eastwood eastwood

kondo:
	lein with-profile -dev,+$(VERSION),+clj-kondo run -m clj-kondo.main --lint src test

# When releasing, the BUMP variable controls which field in the
# version string will be incremented in the *next* snapshot
# version. Typically this is either "major", "minor", or "patch".

BUMP ?= patch

release:
	lein with-profile -user,+$(VERSION) release $(BUMP)

# Deploying requires the caller to set environment variables as
# specified in project.clj to provide a login and password to the
# artifact repository.

# GIT_TAG=v3.2.0 CLOJARS_USERNAME=$USER CLOJARS_PASSWORD=$(pbpaste) make deploy
deploy: check-env .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config deploy clojars
	git tag -a "$$GIT_TAG" -m "$$GIT_TAG"
	git push
	git push --tags

jar: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config jar

install: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config install

check-env:
ifndef CLOJARS_USERNAME
	$(error CLOJARS_USERNAME is undefined)
endif
ifndef CLOJARS_PASSWORD
	$(error CLOJARS_PASSWORD is undefined)
endif
ifndef GIT_TAG
	$(error GIT_TAG is undefined)
endif
