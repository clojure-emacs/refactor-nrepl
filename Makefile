.PHONY: inline-deps test release deploy clean

VERSION ?= 1.10

clean:
	lein clean
	rm -f .inline-deps

.inline-deps: clean
	lein with-profile -user,+$(VERSION) inline-deps
	touch .inline-deps

inline-deps: .inline-deps

test: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config test

cljfmt:
	lein with-profile -user,+$(VERSION),+cljfmt cljfmt check

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

deploy: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config deploy clojars

jar: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config jar

install: .inline-deps
	lein with-profile -user,+$(VERSION),+plugin.mranderson/config install
