# Automapa Map Services Java SDK - every command runs inside the "maven" docker compose service.

UID := $(shell id -u)
GID := $(shell id -g)
export UID GID

# Extra arguments passed to Maven, e.g. make test-unit ARGS="-Dtest=JsonTest"
ARGS ?=

COMPOSE := docker compose
RUN     := $(COMPOSE) run --rm maven
MVN     := $(RUN) mvn -B --no-transfer-progress -Duser.home=/var/maven

# Quality gate steps run by "make check", in this order.
CHECK_STEPS := format-check lint typecheck javadoc test-unit

# A selective run (ARGS="-Dtest=...") cannot reach the coverage threshold, so it only reports coverage.
COVERAGE_ARGS := $(if $(findstring -Dtest=,$(ARGS)),-Djacoco.haltOnFailure=false,)

.DEFAULT_GOAL := help

.PHONY: help build test test-unit test-integration format format-check lint typecheck javadoc check examples \
	package shell clean m2

help: ## Show available targets
	@echo "Usage: make <target> [ARGS=\"<maven arguments>\"]"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Next: run 'make build' to compile the SDK."

m2:
	@mkdir -p .m2

build: m2 ## Compile main and test sources (-Xlint:all -Werror)
	$(MVN) test-compile $(ARGS)
	@echo "Next: run 'make test-unit' to execute unit tests."

test-unit: m2 ## Run unit tests (without live API tests) with JaCoCo line coverage >= 85%
	$(MVN) test $(COVERAGE_ARGS) $(ARGS)
	@echo "Coverage report: target/site/jacoco/index.html"
	@echo "Next: run 'make test-integration' to check the SDK against the live API."

test-integration: m2 ## Run live API tests (@Tag("live-api"), requires .env)
	@if [ ! -f .env ]; then \
		echo "Missing .env: run \`cp .env.example .env\` and fill AUTOMAPA_API_KEY / AUTOMAPA_API_PASSWORD"; \
		exit 1; \
	fi
	$(MVN) test -Plive $(ARGS)
	@echo "Next: run 'make package' to build the jar."

test: test-unit test-integration ## Run unit tests and live API tests
	@echo "Next: run 'make package' to build the jar."

format: m2 ## Format Java sources (Spotless, palantir-java-format)
	$(MVN) spotless:apply $(ARGS)
	@echo "Next: run 'make check' to verify the whole quality gate."

format-check: m2 ## Verify formatting without changing files
	$(MVN) spotless:check $(ARGS)
	@echo "Next: run 'make lint' (or 'make format' to fix formatting)."

lint: m2 ## Run Checkstyle (config/checkstyle/checkstyle.xml)
	$(MVN) checkstyle:check $(ARGS)
	@echo "Next: run 'make typecheck'."

typecheck: m2 ## Compile main, tests and examples/*.java with -Xlint:all -Werror
	$(MVN) test-compile $(ARGS)
	$(RUN) docker/maven/compile-examples.sh
	@echo "Next: run 'make javadoc'."

javadoc: m2 ## Generate Javadoc with doclint (every problem fails)
	$(MVN) javadoc:javadoc $(ARGS)
	@echo "Next: run 'make test-unit'."

check: m2 ## Quality gate: format-check lint typecheck javadoc test-unit (collects all failures)
	@failed=""; \
	for step in $(CHECK_STEPS); do \
		echo "=== make $$step ==="; \
		$(MAKE) --no-print-directory $$step || failed="$$failed $$step"; \
	done; \
	if [ -z "$$failed" ]; then \
		echo "CHECK PASSED"; \
		echo "Next: commit your change."; \
	else \
		echo "CHECK FAILED:$$failed"; \
		echo "Next: fix the failed steps (formatting: 'make format') and run 'make check' again."; \
		exit 1; \
	fi

examples: m2 ## Run examples/*.java of the services in AUTOMAPA_API_REQUIRED_ACCESS on the live API (EXAMPLES_DRY_RUN=1: plan only)
	@if [ ! -f .env ]; then \
		echo "Missing .env: run \`cp .env.example .env\` and fill AUTOMAPA_API_KEY / AUTOMAPA_API_PASSWORD"; \
		exit 1; \
	fi
	$(MVN) compile $(ARGS)
	$(COMPOSE) run --rm -e EXAMPLES_DRY_RUN maven docker/maven/run-examples.sh
	@echo "Next: run 'make check' before committing."

package: m2 ## Build the jar, -sources.jar and -javadoc.jar into target/ and verify their contents
	$(MVN) package source:jar-no-fork javadoc:jar -DskipTests $(ARGS)
	$(RUN) docker/maven/verify-package.sh
	@echo "Next: find the jars in target/; publishing steps are described in the release profile of pom.xml."

shell: m2 ## Open a shell in the maven container
	$(COMPOSE) run --rm maven bash
	@echo "Next: run 'make build' or 'make test-unit'."

clean: m2 ## Remove build output (target/)
	$(MVN) clean $(ARGS)
	@echo "Next: run 'make build' to compile again."
