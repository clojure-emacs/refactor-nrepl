.PHONY: inline-deps test deploy clean

VERSION ?= 1.11

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
	lein with-profile -user,+$(VERSION),+deploy,+eastwood eastwood

kondo:
	lein with-profile -dev,+$(VERSION),+clj-kondo run -m clj-kondo.main --lint src test


# Deployment is performed via CI by creating a git tag prefixed with "v".
# Please do not deploy locally as it skips various measures (particularly around mranderson).
deploy: check-env .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config deploy clojars

jar: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config jar

# Usage: PROJECT_VERSION=3.7.1 make install
# PROJECT_VERSION is needed because it's not computed dynamically
install: check-install-env .inline-deps
	 LEIN_JVM_OPTS="-Dmranderson.internal.no-parallelism=true" lein with-profile -user,+$(VERSION),+plugin.mranderson/config install

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

check-install-env:
ifndef PROJECT_VERSION
	$(error Please set PROJECT_VERSION as an env var beforehand.)
endif
