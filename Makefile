.PHONY: clean inline-deps fast-test quick-test test cljfmt cljfmt-fix eastwood kondo lint deploy jar install check-env

VERSION ?= 1.12
# The version local installs get. Releases take theirs from the git tag.
PROJECT_VERSION ?= 99.99
export PROJECT_VERSION

clean:
	lein clean
	rm -f .inline-deps

.inline-deps: clean
	lein with-profile -user,-dev,+$(VERSION) inline-deps
	touch .inline-deps

inline-deps: .inline-deps

fast-test: clean
	lein with-profile -user,-dev,+$(VERSION) test

quick-test: fast-test

test: .inline-deps
	lein with-profile -user,-dev,+$(VERSION),+plugin.mranderson/config test

cljfmt:
	lein with-profile -user,-dev,+$(VERSION),+cljfmt cljfmt check

cljfmt-fix:
	lein with-profile -user,-dev,+$(VERSION),+cljfmt cljfmt fix

eastwood:
	lein with-profile -user,-dev,+$(VERSION),+deploy,+test,+eastwood eastwood

kondo:
	lein with-profile -user,-dev,+$(VERSION),+clj-kondo run -m clj-kondo.main --lint src test

lint: kondo cljfmt eastwood

# Deployment is performed via CI by creating a git tag prefixed with "v".
# Please do not deploy locally as it skips various measures (particularly around mranderson).
deploy: check-env .inline-deps
	lein with-profile -user,-dev,+$(VERSION),+plugin.mranderson/config deploy clojars

jar: .inline-deps
	lein with-profile -user,-dev,+$(VERSION),+plugin.mranderson/config jar

# Usage: make install (or PROJECT_VERSION=3.12.0 make install)
install: .inline-deps
	 LEIN_JVM_OPTS="-Dmranderson.internal.no-parallelism=true" lein with-profile -user,-dev,+$(VERSION),+plugin.mranderson/config install

check-env:
ifndef CLOJARS_USERNAME
	$(error CLOJARS_USERNAME is undefined)
endif
ifndef CLOJARS_PASSWORD
	$(error CLOJARS_PASSWORD is undefined)
endif
ifndef CIRCLE_TAG
	$(error CIRCLE_TAG is undefined)
endif
