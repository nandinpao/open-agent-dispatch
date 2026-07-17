SHELL := /usr/bin/env bash
PROJECT_NAME ?= opendispatch
CI_OUTPUT_DIR ?= .ci-output
ENV_FILE ?= $(shell if [ -f deploy/env/.env.local ]; then echo deploy/env/.env.local; else echo deploy/env/.env.local.example; fi)
CI_ENV_FILE ?= deploy/env/.env.local.ci
COMPOSE_FILE ?= deploy/docker-compose.local.yml
CI_COMPOSE_FILE ?= deploy/docker-compose.ci.yml
COMPOSE_LOCAL := docker compose -p $(PROJECT_NAME) --env-file $(ENV_FILE) -f $(COMPOSE_FILE)
COMPOSE_CI := docker compose -p $(PROJECT_NAME)-ci --env-file $(CI_ENV_FILE) -f $(CI_COMPOSE_FILE)

.PHONY: help verify-current verify-legacy-release verify-stage0-dispatch-authority-freeze phase0-dispatch-authority-freeze check-project-layout repair-project-layout check-toolchain verify-stage2-sql-tenant-contract characterize-stage2-report characterize-stage2-dry-run characterize-stage2-strict test-stage2-admin-tenant-contract test-stage2-postgres-optional-filters stage2-sql-tenant-contract characterize-stage3-report characterize-stage3-dry-run characterize-stage3-strict verify-stage3-dispatch-flow-aggregate test-stage3-dispatch-flow-aggregate stage3-dispatch-flow-aggregate validate verify-observability-dependency-policy verify-observability-dependency-tree verify-slf4j-direct-dependencies verify-p1b-otlp-collector verify-p2a-http-observation verify-p2b-core-business-observation verify-p3a-spring-managed-context-propagation verify-p3b-manual-executor-outbound-http verify-p3c-agent-protocol-trace-propagation verify-p4a-core-admin-authentication verify-p4b-admin-ui-core-authentication verify-p4c-authentication-final-convergence verify-p5a-production-otel-hardening validate-p5a-production-otel-runtime smoke-p4b-auth smoke-p4c-auth smoke-otlp build-core build-netty build-admin docker-core docker-netty docker-admin docker-all up up-agent down down-v smoke test-admin-strict test-source-clean clean-artifacts ci-fast ci-pr ci-release ci-release-dry-run ci-local ci-local-teardown cd-local ci-ps ci-open ci-smoke ci-report ci-diagnose ci-port-check ci-down ci-down-v ci-logs clean-ci install-ci-workflows release-package release-package-offline release-notes verify verify-prod-security verify-p2m1-dispatch-contract verify-p2m2-dispatch-policy-contract verify-p2p-cms-content-review-contract verify-p2p-cms-content-review-api verify-p2r-dispatch-contract-release-gate verify-p3m-enforce-runtime-acceptance verify-p3n-full-enforce-acceptance verify-p3o-enforce-hardening verify-p3p-production-observability verify-stage9-agent-setup verify-r0-dispatch-flow-rule-inventory verify-r1-dispatch-flow-ui-ia verify-r7-test-trace-chain verify-r6-flow-rule-routing-engine verify-r5-flow-owned-agent-assignment verify-r4-flow-owned-capabilities-skills verify-r3-event-stage-a2a-envelope verify-r2-flow-owned-rule-model verify-r10-flow-rule-a2a-regression acceptance-r10-flow-rule-a2a-regression verify-p0-1-r12-14-v108-baseline verify-p0-2-flow-rule-dispatchability verify-p1-db-backed-dispatch-flow-crud verify-p2-flow-rule-dry-run-readiness verify-p3-beginner-three-step-dispatch-flow-ui verify-p0-zero-special-case-dispatch verify-p1-generic-dispatch-governance-model verify-p2-requirement-resolver-shadow-mode verify-p3-eligibility-shadow-evaluators verify-p4-generic-candidate-routing-shadow verify-p5-runtime-hardcode-removal verify-p7-dispatch-governance-admin-ui verify-p8-action-grant-workflow verify-p9-effectful-action-runtime-integration verify-p10-generic-dispatch-authoritative-cutover verify-p11-legacy-control-path-decommission verify-p11-assignment-diagnostics

help:
	@echo "OpenDispatch local automation"
	@echo ""
	@echo "Daily commands:"
	@echo "  make verify          Run the single current app/config verification gate"
	@echo "  make cd-local        Build artifacts, start local stack, and smoke test"
	@echo "  make down-v          Stop local stack and remove volumes"
	@echo "  make collect-dispatch-logs Collect Core/Netty/Admin UI/Agent diagnostics"
	@echo "  make test-admin-strict Run Admin UI typecheck, lint, tests, and build"
	@echo "  make check-toolchain Verify Java / Maven / Node / npm / Docker Compose"
	@echo ""
	@echo "Policy: make verify is the only daily verification entrypoint."
	@echo "Advanced and archived targets are intentionally hidden from default help."
	@echo "Use: make -qp | sed -n 's/:.*//p' | sort -u"
	@echo ""

check-project-layout:
	python3 scripts/maintenance/repair_nested_project_layout.py

verify-stage0-dispatch-authority-freeze:
	python3 scripts/verify/verify-stage0-dispatch-authority-freeze.py

phase0-dispatch-authority-freeze: verify-stage0-dispatch-authority-freeze


repair-project-layout:
	python3 scripts/maintenance/repair_nested_project_layout.py --apply

check-toolchain:
	./scripts/dev/check-toolchain.sh

validate:
	mvn -f pom.xml validate

build-core:
	mvn -U -f ai-event-gateway-core/pom.xml -pl control-plane-app -am package -DskipTests

build-netty:
	mvn -U -f ai-event-gateway-netty/pom.xml -pl gateway-app -am package -DskipTests

build-admin:
	./scripts/ci/admin-ui-clean-generated.sh --best-effort
	cd ai-event-gateway-admin-ui && npm ci && NEXT_DIST_DIR=.next-ci npm run build

docker-core:
	./scripts/build-core-image.sh

docker-netty:
	./scripts/build-netty-image.sh

docker-admin:
	./scripts/build-admin-ui-image.sh

docker-all:
	./scripts/build-all-images.sh

up:
	./scripts/local-compose-up.sh

up-agent:
	WITH_AGENT=true ./scripts/local-compose-up.sh

down:
	./scripts/ci/local-admin-ui-host.sh stop --env-file $(ENV_FILE) --output-dir $(CI_OUTPUT_DIR) || true
	$(COMPOSE_LOCAL) down --remove-orphans

down-v:
	./scripts/ci/local-admin-ui-host.sh stop --env-file $(ENV_FILE) --output-dir $(CI_OUTPUT_DIR) || true
	$(COMPOSE_LOCAL) down -v --remove-orphans

smoke:
	./scripts/ci/local-smoke.sh --project $(PROJECT_NAME) --compose-file $(COMPOSE_FILE) --env-file $(ENV_FILE)

smoke-otlp:
	./scripts/observability/otlp-export-smoke.sh --project $(PROJECT_NAME) --compose-file $(COMPOSE_FILE) --env-file $(ENV_FILE) --output-dir $(CI_OUTPUT_DIR)

test-admin-strict:
	./scripts/ci/admin-ui-clean-generated.sh --best-effort
	cd ai-event-gateway-admin-ui && npm ci && npm run typecheck && npm run lint && npm run test:normalizers && npm run test:api-envelope && NEXT_DIST_DIR=.next-ci npm run build

test-source-clean:
	./scripts/ci/source-clean-check.sh

clean-artifacts:
	./scripts/ci/clean-artifacts.sh

ci-fast:
	./scripts/ci/local-ci.sh --fast

ci-pr:
	$(MAKE) install-ci-workflows
	$(MAKE) verify
	$(MAKE) verify-observability-dependency-tree
	$(MAKE) ci-fast
	cd ai-event-gateway-core && mvn -pl control-plane-app -am -Dgroups=container -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -Dtest='AgentGovernanceRepositoryDbHardeningContainerTest,DispatchRequestRepositoryDbHardeningContainerTest,TaskCallbackRepositoryDbHardeningContainerTest,RemediationWorkflowRepositoryDbHardeningContainerTest,SkillRegistryRepositoryDbHardeningContainerTest' test
	$(MAKE) test-admin-strict
	$(MAKE) clean-artifacts
	$(MAKE) test-source-clean

ci-release:
	$(MAKE) ci-pr
	GATEWAY_AGENT_AUTHORIZATION_ENABLED=true RUN_RUNTIME_LIFECYCLE_E2E=true $(MAKE) ci-local-teardown

ci-release-dry-run:
	./scripts/release/ci-release-dry-run.sh

ci-local:
	./scripts/ci/local-ci.sh --keep-stack

ci-local-teardown:
	./scripts/ci/local-ci.sh --teardown

cd-local:
	./scripts/ci/local-cd.sh

ci-ps:
	./scripts/ci/local-status.sh --project $(PROJECT_NAME)-ci --compose-file $(CI_COMPOSE_FILE) --env-file $(CI_ENV_FILE)

ci-open:
	./scripts/ci/local-open.sh --project $(PROJECT_NAME)-ci --compose-file $(CI_COMPOSE_FILE) --env-file $(CI_ENV_FILE) --browser

ci-smoke:
	./scripts/ci/local-smoke.sh --project $(PROJECT_NAME)-ci --compose-file $(CI_COMPOSE_FILE) --env-file $(CI_ENV_FILE)

ci-report:
	./scripts/ci/local-report.sh --project $(PROJECT_NAME)-ci --compose-file $(CI_COMPOSE_FILE) --env-file $(CI_ENV_FILE)

ci-diagnose:
	./scripts/ci/local-diagnose.sh --project $(PROJECT_NAME)-ci --compose-file $(CI_COMPOSE_FILE) --env-file $(CI_ENV_FILE)

ci-port-check:
	./scripts/ci/local-port-check.sh --env-file $(CI_ENV_FILE)

ci-logs:
	$(COMPOSE_CI) logs -f --tail=300

ci-down:
	./scripts/ci/local-admin-ui-host.sh stop --env-file $(CI_ENV_FILE) --output-dir $(CI_OUTPUT_DIR) || true
	$(COMPOSE_CI) down --remove-orphans

ci-down-v:
	./scripts/ci/local-admin-ui-host.sh stop --env-file $(CI_ENV_FILE) --output-dir $(CI_OUTPUT_DIR) || true
	$(COMPOSE_CI) down -v --remove-orphans

clean-ci:
	./scripts/ci/local-clean.sh

install-ci-workflows:
	./scripts/release/install-ci-workflows.sh

verify: verify-current

.PHONY: verify-current verify-current-app-contract verify-p21-api-runtime-acceptance verify-phase32
verify-current:
	$(MAKE) verify-stage8-f0f-flow-agent-setup-error-contract
	$(MAKE) verify-stage8-dispatch-authority-unification
	$(MAKE) verify-local-compose-no-host-bind-mounts
	$(MAKE) verify-p21-api-runtime-acceptance
	$(MAKE) verify-current-app-contract
	$(MAKE) phase32-i-acceptance-dry-run

verify-current-app-contract:
	python3 scripts/verify/verify-current-app-contract.py

verify-p21-api-runtime-acceptance:
	python3 scripts/verify/verify-p21-api-runtime-acceptance.py


.PHONY: verify-local-compose-no-host-bind-mounts
verify-local-compose-no-host-bind-mounts:
	python3 scripts/verify/verify-local-compose-no-host-bind-mounts.py

verify-legacy-release:
	python3 scripts/verify/verify-release.py
	python3 scripts/verify/verify-stage0-integration-characterization.py
	python3 scripts/architecture/stage0_dispatch_feature_freeze.py
	python3 scripts/verify/verify-stage1-backend-golden-path.py

verify-stage0-integration-characterization:
	python3 scripts/verify/verify-stage0-integration-characterization.py

verify-stage0-dispatch-feature-freeze:
	python3 scripts/architecture/stage0_dispatch_feature_freeze.py

characterize-stage0-static:
	python3 scripts/characterization/stage0_static_characterization.py

characterize-stage0-failure-map:
	python3 scripts/characterization/stage0_failure_map.py

characterize-stage0-dry-run:
	./scripts/characterization/run-stage0.sh --dry-run

characterize-stage0-live:
	./scripts/characterization/run-stage0.sh --live

characterize-stage0-strict:
	./scripts/characterization/run-stage0.sh --strict

verify-stage1-backend-golden-path:
	python3 scripts/verify/verify-stage1-backend-golden-path.py

test-stage1-backend-golden-path:
	mvn -pl ai-event-gateway-core/task-orchestration -am \
		-Dtest=GenericDispatchRequirementResolverTest,DispatchEligibilityShadowEvaluatorsTest,GenericDispatchEligibilityServiceStandardFlowTest,DispatchEligibilityPolicyFlowRuleTest,GenericCandidateAgentProviderStandardFlowTest,InMemoryTaskRepositoryFlowRecoveryTest \
		-Dsurefire.failIfNoSpecifiedTests=false test

test-stage1-characterization-auth:
	node scripts/acceptance/stage1-characterization-auth-self-test.mjs

characterize-stage1-dry-run:
	STAGE1_ENV_FILE="$(ENV_FILE)" ./scripts/characterization/run-stage1.sh --dry-run

characterize-stage1-live:
	STAGE1_ENV_FILE="$(ENV_FILE)" ./scripts/characterization/run-stage1.sh --live

characterize-stage1-strict:
	STAGE1_ENV_FILE="$(ENV_FILE)" ./scripts/characterization/run-stage1.sh --strict

verify-stage2-sql-tenant-contract:
	python3 scripts/verify/verify-stage2-sql-tenant-contract.py

characterize-stage2-report:
	python3 scripts/characterization/stage2_tenant_sql_contract.py --strict

characterize-stage2-dry-run:
	./scripts/characterization/run-stage2.sh --dry-run

characterize-stage2-strict:
	./scripts/characterization/run-stage2.sh --strict

test-stage2-admin-tenant-contract:
	cd ai-event-gateway-admin-ui && npm run test:stage2-tenant-context

test-stage2-postgres-optional-filters:
	cd ai-event-gateway-core && mvn -pl control-plane-app -am -Dgroups=container -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -Dtest=Stage2PostgresOptionalFilterContainerTest test

stage2-sql-tenant-contract:
	$(MAKE) characterize-stage2-dry-run
	$(MAKE) test-stage2-admin-tenant-contract
	$(MAKE) test-stage2-postgres-optional-filters

characterize-stage3-report:
	python3 scripts/characterization/stage3_flow_aggregate_contract.py

characterize-stage3-dry-run:
	./scripts/characterization/run-stage3.sh --dry-run

characterize-stage3-strict:
	./scripts/characterization/run-stage3.sh --strict

verify-stage3-dispatch-flow-aggregate:
	python3 scripts/verify/verify-stage3-dispatch-flow-aggregate.py

test-stage3-dispatch-flow-aggregate:
	$(MAKE) check-toolchain
	@docker info >/dev/null 2>&1 || (echo "ERROR: Docker daemon is required for Stage 3 PostgreSQL Testcontainers." >&2; exit 1)
	cd ai-event-gateway-core && mvn -pl control-plane-app -am -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -Dtest=Stage3DispatchFlowAggregateTransactionContainerTest,DispatchFlowControllerAggregateMutationTest test

stage3-dispatch-flow-aggregate:
	$(MAKE) characterize-stage3-report
	$(MAKE) verify-stage3-dispatch-flow-aggregate
	$(MAKE) test-stage3-dispatch-flow-aggregate

.PHONY: verify-stage4-beginner-ui-navigation test-stage4-admin-ui stage4-beginner-ui-navigation
verify-stage4-beginner-ui-navigation:
	cd ai-event-gateway-admin-ui && npm run verify:stage4-beginner-ui

test-stage4-admin-ui:
	cd ai-event-gateway-admin-ui && npm run test:stage4-beginner-ui

stage4-beginner-ui-navigation:
	$(MAKE) verify-stage4-beginner-ui-navigation
	$(MAKE) test-stage4-admin-ui

verify-observability-dependency-policy:
	python3 scripts/verify/verify-observability-dependency-policy.py

verify-observability-dependency-tree:
	./scripts/verify/verify-observability-dependency-tree.sh

verify-slf4j-direct-dependencies:
	python3 scripts/verify/verify-slf4j-direct-dependencies.py

verify-p1b-otlp-collector:
	python3 scripts/verify/verify-p1b-otlp-collector-config.py

verify-p2a-http-observation:
	python3 scripts/verify/verify-p2a-http-observation.py

verify-p2b-core-business-observation:
	python3 scripts/verify/verify-p2b-core-business-observation.py

verify-p3a-spring-managed-context-propagation:
	python3 scripts/verify/verify-p3a-spring-managed-context-propagation.py

verify-p3b-manual-executor-outbound-http:
	python3 scripts/verify/verify-p3b-manual-executor-outbound-http.py

verify-p3c-agent-protocol-trace-propagation:
	python3 scripts/verify/verify-p3c-agent-protocol-trace-propagation.py

verify-p4a-core-admin-authentication:
	python3 scripts/verify/verify-p4a-core-admin-authentication.py

verify-p4b-admin-ui-core-authentication:
	python3 scripts/verify/verify-p4b-admin-ui-core-authentication.py

verify-p4c-authentication-final-convergence:
	python3 scripts/verify/verify-p4c-authentication-final-convergence.py

verify-p5a-production-otel-hardening:
	python3 scripts/verify/verify-p5a-production-otel-hardening.py

validate-p5a-production-otel-runtime:
	./scripts/observability/validate-production-otel.sh

smoke-p4b-auth:
	cd ai-event-gateway-admin-ui && npm run smoke:p4b-auth

smoke-p4c-auth:
	bash scripts/security/p4c-auth-browser-e2e.sh

verify-p0-zero-special-case-dispatch:
	python3 scripts/verify/verify-p0-zero-special-case-dispatch.py

verify-p1-generic-dispatch-governance-model:
	python3 scripts/verify/verify-p1-generic-dispatch-governance-model.py

verify-p2-requirement-resolver-shadow-mode:
	python3 scripts/verify/verify-p2-requirement-resolver-shadow-mode.py

verify-prod-security:
	./scripts/verify/verify-prod-security.sh

verify-p2m1-dispatch-contract:
	cd ai-event-gateway-core && ./scripts/verify/verify-p2m1-dispatch-contract-integrity.sh

verify-p2m2-dispatch-policy-contract:
	cd ai-event-gateway-core && ./scripts/verify/verify-p2m2-dispatch-policy-contract-integrity.sh

verify-p2p-cms-content-review-contract:
	cd ai-event-gateway-core && ./scripts/verify/verify-p2p-cms-content-review-contract.sh

verify-p2p-cms-content-review-api:
	cd ai-event-gateway-core && ./scripts/verify/verify-p2p-cms-content-review-eligibility-api.sh

verify-p2r-dispatch-contract-release-gate:
	./scripts/verify/verify-p2r-dispatch-contract-release-gate.sh

verify-p3m-enforce-runtime-acceptance:
	cd ai-event-gateway-admin-ui && npm run verify:p3m-runtime-cutover

verify-p3n-full-enforce-acceptance:
	cd ai-event-gateway-admin-ui && npm run verify:p3n-full-enforce

verify-p3o-enforce-hardening:
	cd ai-event-gateway-admin-ui && npm run verify:p3o-hardening

verify-p3p-production-observability:
	cd ai-event-gateway-admin-ui && npm run verify:p3p-observability

verify-stage9-agent-setup:
	cd ai-event-gateway-admin-ui && npm run verify:stage9-agent-setup

release-notes:
	VERSION="$(VERSION)" ./scripts/release/generate-release-notes.sh

release-package:
	VERSION="$(VERSION)" ./scripts/release/build-release-package.sh $(RELEASE_ARGS)

release-package-offline:
	VERSION="$(VERSION)" INCLUDE_ADMIN_RUNTIME_DEPS=true ./scripts/release/build-release-package.sh --include-admin-runtime-deps $(RELEASE_ARGS)


verify-stage10-agent-readiness:
	cd ai-event-gateway-admin-ui && npm run verify:stage10-agent-readiness

verify-stage11-runtime-readiness:
	cd ai-event-gateway-admin-ui && npm run verify:stage11-runtime-readiness

.PHONY: verify-stage12-runtime-troubleshooting
verify-stage12-runtime-troubleshooting:
	cd ai-event-gateway-admin-ui && npm run verify:stage12-runtime-troubleshooting

.PHONY: verify-stage13-auth-failure
verify-stage13-auth-failure:
	cd ai-event-gateway-admin-ui && npm run verify:stage13-auth-failure

verify-stage14-connection-repair:
	cd ai-event-gateway-admin-ui && npm run verify:stage14-connection-repair

.PHONY: verify-stage15-capability-readiness
verify-stage15-capability-readiness:
	cd ai-event-gateway-admin-ui && npm run verify:stage15-capability-readiness

verify-stage16-effective-capability:
	cd ai-event-gateway-admin-ui && npm run verify:stage16-effective-capability

verify-stage17-runtime-capability:
	cd ai-event-gateway-admin-ui && npm run verify:stage17-runtime-capability

.PHONY: verify-stage18-service-scope-activation
verify-stage18-service-scope-activation:
	cd ai-event-gateway-admin-ui && npm run verify:stage18-service-scope-activation

.PHONY: verify-stage19-dispatch-e2e
verify-stage19-dispatch-e2e:
	cd ai-event-gateway-admin-ui && npm run verify:stage19-dispatch-e2e

verify-stage20-dispatch-evidence:
	cd ai-event-gateway-admin-ui && npm run verify:stage20-dispatch-evidence

.PHONY: verify-stage21-failure-reasons
verify-stage21-failure-reasons:
	cd ai-event-gateway-admin-ui && npm run verify:stage21-failure-reasons

.PHONY: verify-stage22-runtime-binding
verify-stage22-runtime-binding:
	cd ai-event-gateway-admin-ui && npm run verify:stage22-runtime-binding

.PHONY: verify-stage23-delivery-confirmation
verify-stage23-delivery-confirmation:
	cd ai-event-gateway-admin-ui && npm run verify:stage23-delivery-confirmation

verify-r0-dispatch-flow-rule-inventory:
	cd ai-event-gateway-admin-ui && npm run verify:r0-dispatch-flow-rule-inventory


.PHONY: verify-r7-test-trace-chain verify-r6-flow-rule-routing-engine verify-r5-flow-owned-agent-assignment verify-r4-flow-owned-capabilities-skills verify-r3-event-stage-a2a-envelope verify-r2-flow-owned-rule-model verify-r10-flow-rule-a2a-regression acceptance-r10-flow-rule-a2a-regression
verify-r5-flow-owned-agent-assignment:
	cd ai-event-gateway-admin-ui && npm run verify:r5-flow-owned-agent-assignment

verify-r4-flow-owned-capabilities-skills:
	cd ai-event-gateway-admin-ui && npm run verify:r4-flow-owned-capabilities-skills

verify-r3-event-stage-a2a-envelope:
	cd ai-event-gateway-admin-ui && npm run verify:r3-event-stage-a2a-envelope

verify-r2-flow-owned-rule-model:
	cd ai-event-gateway-admin-ui && npm run verify:r2-flow-owned-rule-model

.PHONY: verify-r1-dispatch-flow-ui-ia
verify-r1-dispatch-flow-ui-ia:
	cd ai-event-gateway-admin-ui && npm run verify:r1-dispatch-flow-ui-ia

.PHONY: verify-r6-flow-rule-routing-engine
verify-r6-flow-rule-routing-engine:
	cd ai-event-gateway-admin-ui && npm run verify:r6-flow-rule-routing-engine

.PHONY: verify-r7-test-trace-chain
verify-r7-test-trace-chain:
	cd ai-event-gateway-admin-ui && npm run verify:r7-test-trace-chain

.PHONY: verify-r8-task-case-timeline-repair
verify-r8-task-case-timeline-repair:
	cd ai-event-gateway-admin-ui && npm run verify:r8-task-case-timeline-repair

.PHONY: verify-r9-legacy-crud-fallback-removal
verify-r9-legacy-crud-fallback-removal:
	cd ai-event-gateway-admin-ui && npm run verify:r9-legacy-crud-fallback-removal

.PHONY: verify-r10-flow-rule-a2a-regression
verify-r10-flow-rule-a2a-regression:
	cd ai-event-gateway-admin-ui && npm run verify:r10-flow-rule-a2a-regression

.PHONY: acceptance-r10-flow-rule-a2a-regression
acceptance-r10-flow-rule-a2a-regression:
	cd ai-event-gateway-admin-ui && npm run acceptance:r10-flow-rule-a2a-regression -- --dry-run

.PHONY: verify-r11-logback-dispatch-trace-logging
verify-r11-logback-dispatch-trace-logging:
	cd ai-event-gateway-admin-ui && npm run verify:r11-logback-dispatch-trace-logging

.PHONY: verify-r12-micrometer-trace-span-logging
verify-r12-micrometer-trace-span-logging:
	cd ai-event-gateway-admin-ui && npm run verify:r12-micrometer-trace-span-logging


.PHONY: verify-p0-1-r12-14-v108-baseline
verify-p0-1-r12-14-v108-baseline:
	python3 scripts/verify/verify-p0-1-r12-14-v108-baseline.py

.PHONY: verify-p0-2-flow-rule-dispatchability
verify-p0-2-flow-rule-dispatchability:
	python3 scripts/verify/verify-p0-2-flow-rule-dispatchability.py

.PHONY: verify-p1-db-backed-dispatch-flow-crud
verify-p1-db-backed-dispatch-flow-crud:
	python3 scripts/verify/verify-p1-db-backed-dispatch-flow-crud.py

.PHONY: verify-p3-eligibility-shadow-evaluators
verify-p3-eligibility-shadow-evaluators:
	python3 scripts/verify/verify-p3-eligibility-shadow-evaluators.py

.PHONY: verify-p4-generic-candidate-routing-shadow
verify-p4-generic-candidate-routing-shadow:
	python3 scripts/verify/verify-p4-generic-candidate-routing-shadow.py

.PHONY: verify-p5-runtime-hardcode-removal
verify-p5-runtime-hardcode-removal:
	python3 scripts/verify/verify-p5-runtime-hardcode-removal.py

.PHONY: verify-p2-flow-rule-dry-run-readiness
verify-p2-flow-rule-dry-run-readiness:
	python3 scripts/verify/verify-p2-flow-rule-dry-run-readiness.py

.PHONY: verify-p3-beginner-three-step-dispatch-flow-ui
verify-p3-beginner-three-step-dispatch-flow-ui:
	python3 scripts/verify/verify-p3-beginner-three-step-dispatch-flow-ui.py

.PHONY: verify-p4-task-detail-flow-repair-center
verify-p4-task-detail-flow-repair-center:
	python3 scripts/verify/verify-p4-task-detail-flow-repair-center.py

.PHONY: verify-p5-legacy-feature-demotion
verify-p5-legacy-feature-demotion:
	python3 scripts/verify/verify-p5-legacy-feature-demotion.py

.PHONY: verify-p6-empty-db-flow-rule-e2e
verify-p6-empty-db-flow-rule-e2e:
	python3 scripts/verify/verify-p6-empty-db-flow-rule-e2e.py

.PHONY: acceptance-p6-empty-db-flow-rule-e2e-dry-run
acceptance-p6-empty-db-flow-rule-e2e-dry-run:
	node scripts/acceptance/p6-empty-db-flow-rule-e2e.mjs --dry-run

.PHONY: verify-p6-2-dispatch-log-path-and-flow-rule-sql-diagnostics
verify-p6-2-dispatch-log-path-and-flow-rule-sql-diagnostics:
	python3 scripts/verify/verify-p6-2-dispatch-log-path-and-flow-rule-sql-diagnostics.py

.PHONY: collect-dispatch-logs
collect-dispatch-logs:
	./scripts/diagnostics/collect-dispatch-logs.sh

.PHONY: verify-p6-3-debug-flow-rule-matching-logs
verify-p6-3-debug-flow-rule-matching-logs:
	python3 scripts/verify/verify-p6-3-debug-flow-rule-matching-logs.py

.PHONY: verify-p6-4-flow-rule-repository-bean-registration
verify-p6-4-flow-rule-repository-bean-registration:
	python3 scripts/verify/verify-p6-4-flow-rule-repository-bean-registration.py

.PHONY: verify-p6-5-runtime-assignable-capacity-fix
verify-p6-5-runtime-assignable-capacity-fix:
	python3 scripts/verify/verify-p6-5-runtime-assignable-capacity-fix.py

.PHONY: verify-p6-6-dispatch-delivery-and-callback-logging
verify-p6-6-dispatch-delivery-and-callback-logging:
	python3 scripts/verify/verify-p6-6-dispatch-delivery-and-callback-logging.py

.PHONY: verify-p6-7-flow-rule-dispatch-bypasses-legacy-profile-gate
verify-p6-7-flow-rule-dispatch-bypasses-legacy-profile-gate:
	python3 scripts/verify/verify-p6-7-flow-rule-dispatch-bypasses-legacy-profile-gate.py

.PHONY: verify-p6-8-stale-flow-rule-task-recovery
verify-p6-8-stale-flow-rule-task-recovery:
	python3 scripts/verify/verify-p6-8-stale-flow-rule-task-recovery.py

.PHONY: verify-p6-9-agent-callback-issue-trace
verify-p6-9-agent-callback-issue-trace:
	python3 scripts/verify/verify-p6-9-agent-callback-issue-trace.py

.PHONY: verify-p6-10-callback-ack-durability-replay
verify-p6-10-callback-ack-durability-replay:
	python3 scripts/verify/verify-p6-10-callback-ack-durability-replay.py
.PHONY: verify-p6-11-lifecycle-reassign-and-callback-replay-hardening
verify-p6-11-lifecycle-reassign-and-callback-replay-hardening:
	python3 scripts/verify/verify-p6-11-lifecycle-reassign-and-callback-replay-hardening.py


.PHONY: verify-p6-12-queued-task-auto-assign-retry-and-lifecycle-env
verify-p6-12-queued-task-auto-assign-retry-and-lifecycle-env:
	python3 scripts/verify/verify-p6-12-queued-task-auto-assign-retry-and-lifecycle-env.py

.PHONY: verify-p6-13-terminal-callback-core-ack-cleanup
verify-p6-13-terminal-callback-core-ack-cleanup:
	python3 scripts/verify/verify-p6-13-terminal-callback-core-ack-cleanup.py

.PHONY: verify-p6-15-terminal-task-ui-classification
verify-p6-15-terminal-task-ui-classification:
	python3 scripts/verify/verify-p6-15-terminal-task-ui-classification.py

.PHONY: verify-p6-existing-data-migration-fixture-cleanup
verify-p6-existing-data-migration-fixture-cleanup:
	python3 scripts/verify/verify-p6-existing-data-migration-fixture-cleanup.py

.PHONY: verify-p7-dispatch-governance-admin-ui
verify-p7-dispatch-governance-admin-ui:
	python3 scripts/verify/verify-p7-dispatch-governance-admin-ui.py

.PHONY: verify-p8-action-grant-workflow
verify-p8-action-grant-workflow:
	python3 scripts/verify/verify-p8-action-grant-workflow.py

.PHONY: verify-p9-effectful-action-runtime-integration
verify-p9-effectful-action-runtime-integration:
	python3 scripts/verify/verify-p9-effectful-action-runtime-integration.py

.PHONY: verify-p10-generic-dispatch-authoritative-cutover
verify-p10-generic-dispatch-authoritative-cutover:
	python3 scripts/verify/verify-p10-generic-dispatch-authoritative-cutover.py

.PHONY: build-p6-clean-flyway-baseline
build-p6-clean-flyway-baseline:
	python3 scripts/db/build_clean_flyway_location.py --output build/p6-clean-flyway-migration --report build/p6-clean-flyway-report.json

.PHONY: verify-p11-legacy-control-path-decommission
verify-p11-legacy-control-path-decommission:
	python3 scripts/verify/verify-p11-legacy-control-path-decommission.py

.PHONY: verify-p11-assignment-diagnostics
verify-p11-assignment-diagnostics:
	python3 scripts/verify/verify-p11-assignment-diagnostics.py

.PHONY: verify-stage5-real-event-task-diagnosis characterize-stage5-report characterize-stage5-dry-run characterize-stage5-strict test-stage5-admin-ui test-stage5-core stage5-real-event-task-diagnosis
verify-stage5-real-event-task-diagnosis:
	python3 scripts/verify/verify-stage5-real-event-task-diagnosis.py

characterize-stage5-report:
	python3 scripts/characterization/stage5_real_event_task_diagnosis_contract.py

characterize-stage5-dry-run:
	./scripts/characterization/run-stage5.sh --dry-run

characterize-stage5-strict:
	./scripts/characterization/run-stage5.sh --strict

test-stage5-admin-ui:
	cd ai-event-gateway-admin-ui && npm run test:stage5-real-event-task

test-stage5-core:
	@command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven 3.9+ is required for Stage 5 Core TDD."; exit 1; }
	@java -version 2>&1 | grep -q 'version "25' || { echo "ERROR: Java 25 is required for Stage 5 Core TDD."; exit 1; }
	cd ai-event-gateway-core && mvn -pl control-plane-app -am -Dtest=DispatchFlowControllerRealTestEventTest -Dsurefire.failIfNoSpecifiedTests=false test

stage5-real-event-task-diagnosis:
	$(MAKE) verify-stage0-dispatch-feature-freeze
	$(MAKE) verify-stage1-backend-golden-path
	$(MAKE) verify-stage2-sql-tenant-contract
	$(MAKE) verify-stage3-dispatch-flow-aggregate
	$(MAKE) verify-stage4-beginner-ui-navigation
	$(MAKE) characterize-stage5-report
	$(MAKE) verify-stage5-real-event-task-diagnosis
	$(MAKE) test-stage5-admin-ui
	$(MAKE) test-stage5-core

.PHONY: verify-stage6-recovery-task-actions characterize-stage6-report characterize-stage6-dry-run characterize-stage6-strict test-stage6-admin-ui test-stage6-core stage6-recovery-task-actions
verify-stage6-recovery-task-actions:
	python3 scripts/verify/verify-stage6-recovery-task-actions.py

characterize-stage6-report:
	python3 scripts/characterization/stage6_recovery_task_actions_contract.py

characterize-stage6-dry-run:
	./scripts/characterization/run-stage6.sh --dry-run

characterize-stage6-strict:
	./scripts/characterization/run-stage6.sh --strict

test-stage6-admin-ui:
	cd ai-event-gateway-admin-ui && npm run stage6:recovery-task-actions

test-stage6-core:
	@command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven 3.9+ is required for Stage 6 Core TDD."; exit 1; }
	@java -version 2>&1 | grep -q 'version "25' || { echo "ERROR: Java 25 is required for Stage 6 Core TDD."; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker is required for the Stage 6 PostgreSQL recovery test."; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "ERROR: Docker daemon is not available for the Stage 6 PostgreSQL recovery test."; exit 1; }
	cd ai-event-gateway-core && mvn -pl control-plane-app -am -Dtest=InMemoryTaskRepositoryFlowRecoveryTest,TaskFailureQueueServiceTest,Stage6ConfigurationBlockedRecoveryContainerTest -Dsurefire.failIfNoSpecifiedTests=false test

stage6-recovery-task-actions:
	$(MAKE) verify-stage0-dispatch-feature-freeze
	$(MAKE) verify-stage1-backend-golden-path
	$(MAKE) verify-stage2-sql-tenant-contract
	$(MAKE) verify-stage3-dispatch-flow-aggregate
	$(MAKE) verify-stage4-beginner-ui-navigation
	$(MAKE) verify-stage5-real-event-task-diagnosis
	$(MAKE) characterize-stage6-report
	$(MAKE) verify-stage6-recovery-task-actions
	$(MAKE) verify-p6-8-stale-flow-rule-task-recovery
	$(MAKE) test-stage6-admin-ui
	$(MAKE) test-stage6-core


.PHONY: verify-stage7-legacy-isolation characterize-stage7-report characterize-stage7-dry-run characterize-stage7-strict test-stage7-admin-ui test-stage7-core report-stage7-legacy-inventory stage7-legacy-isolation
verify-stage7-legacy-isolation:
	python3 scripts/verify/verify-stage7-legacy-isolation.py

characterize-stage7-report:
	python3 scripts/characterization/stage7_legacy_isolation_retirement_contract.py

characterize-stage7-dry-run:
	./scripts/characterization/run-stage7.sh --dry-run

characterize-stage7-strict:
	./scripts/characterization/run-stage7.sh --strict

test-stage7-admin-ui:
	cd ai-event-gateway-admin-ui && npm run stage7:legacy-isolation

test-stage7-core:
	@command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven 3.9+ is required for Stage 7 Core tests."; exit 1; }
	@java -version 2>&1 | grep -Eq 'version "25|openjdk version "25' || { echo "ERROR: Java 25 is required for Stage 7 Core tests."; java -version; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker is required for Stage 7 PostgreSQL integration tests."; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "ERROR: Docker daemon is not available for Stage 7 PostgreSQL integration tests."; exit 1; }
	mvn -f ai-event-gateway-core/pom.xml -pl control-plane-app,task-orchestration,execution-control,identity-access -am \
		-Dtest=DispatchEligibilityPolicyFlowRuleTest,DispatchEligibilityServiceStage7Test,AdminRoleStage7LegacySupportTest,GenericDispatchEligibilityServiceStandardFlowTest,GenericCandidateAgentProviderStandardFlowTest,Stage7LegacyIsolationContainerTest \
		-Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test

report-stage7-legacy-inventory:
	python3 scripts/migration/stage7_legacy_dispatch_inventory.py --output "$${STAGE7_INVENTORY_OUTPUT:-.ci-output/stage7-legacy-inventory.json}"

stage7-legacy-isolation:
	$(MAKE) verify-stage0-dispatch-feature-freeze
	$(MAKE) verify-stage1-backend-golden-path
	$(MAKE) verify-stage2-sql-tenant-contract
	$(MAKE) verify-stage3-dispatch-flow-aggregate
	$(MAKE) verify-stage4-beginner-ui-navigation
	$(MAKE) verify-stage5-real-event-task-diagnosis
	$(MAKE) verify-stage6-recovery-task-actions
	$(MAKE) verify-stage7-legacy-isolation
	$(MAKE) characterize-stage7-report
	$(MAKE) verify-p6-7-flow-rule-dispatch-bypasses-legacy-profile-gate
	$(MAKE) verify-p6-8-stale-flow-rule-task-recovery
	$(MAKE) test-stage7-admin-ui
	$(MAKE) test-stage7-core


.PHONY: verify-stage7-fix2 test-stage7-fix2-bootstrap-auth test-stage7-fix2-core acceptance-stage7-fix2 stage7-fix2
verify-stage7-fix2:
	python3 scripts/verify/verify-stage7-fix2-bootstrap-flow-event-agent.py

test-stage7-fix2-bootstrap-auth:
	node scripts/acceptance/stage7-fix2-bootstrap-auth-self-test.mjs

test-stage7-fix2-core:
	@command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven 3.9+ is required for Stage 7 Fix2 Core tests."; exit 1; }
	@java -version 2>&1 | grep -Eq 'version "25|openjdk version "25' || { echo "ERROR: Java 25 is required for Stage 7 Fix2 Core tests."; java -version; exit 1; }
	mvn -f ai-event-gateway-core/pom.xml -pl event-processing,control-plane-app -am \
		-Dtest=EventNormalizerOptionalFieldsTest,EventIntakeApplicationServiceTest,DecisionEngineSummaryTest \
		-Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test

acceptance-stage7-fix2:
	./scripts/acceptance/stage7-fix2-bootstrap-flow-event-agent-e2e.sh

stage7-fix2:
	$(MAKE) verify-stage0-dispatch-feature-freeze
	$(MAKE) verify-stage1-backend-golden-path
	$(MAKE) verify-stage2-sql-tenant-contract
	$(MAKE) verify-stage3-dispatch-flow-aggregate
	$(MAKE) verify-stage4-beginner-ui-navigation
	$(MAKE) verify-stage5-real-event-task-diagnosis
	$(MAKE) verify-stage6-recovery-task-actions
	$(MAKE) verify-stage7-legacy-isolation
	$(MAKE) verify-stage7-fix2
	$(MAKE) test-stage7-fix2-bootstrap-auth
	$(MAKE) test-stage7-fix2-core

.PHONY: verify-stage7-fix3-admin-core-connectivity test-stage7-fix3-admin-core-connectivity stage7-fix3-admin-core-connectivity
verify-stage7-fix3-admin-core-connectivity:
	python3 scripts/verify/verify-stage7-fix3-admin-core-connectivity.py

test-stage7-fix3-admin-core-connectivity:
	cd ai-event-gateway-admin-ui && npm run test:stage7-fix3-admin-core-connectivity

stage7-fix3-admin-core-connectivity:
	$(MAKE) verify-stage7-fix3-admin-core-connectivity
	$(MAKE) test-stage7-fix3-admin-core-connectivity

.PHONY: diagnose-admin-ui-core-connectivity
diagnose-admin-ui-core-connectivity:
	ENV_FILE="$(ENV_FILE)" COMPOSE_FILE="$(COMPOSE_FILE)" PROJECT_NAME="$(PROJECT_NAME)" ./scripts/diagnostics/admin-ui-core-connectivity.sh

.PHONY: verify-stage7-fix4-spring-path-pattern
verify-stage7-fix4-spring-path-pattern:
	python3 scripts/architecture/verify-stage7-fix4-spring-path-pattern.py

.PHONY: verify-stage8-release-gate verify-stage8-dispatch-authority-unification verify-stage8-f0e-agent-runtime-binding-idempotency verify-stage8-f0e-runtime-binding-artifact verify-stage8-f0f-flow-agent-setup-error-contract characterize-stage8-report characterize-stage8-dry-run characterize-stage8-strict test-stage8-admin-ui stage8-release-gate
verify-stage8-release-gate:
	python3 scripts/verify/verify-stage8-release-gate.py

verify-stage8-dispatch-authority-unification:
	python3 scripts/verify/verify-stage8-dispatch-authority-unification.py

verify-stage8-f0e-agent-runtime-binding-idempotency:
	python3 scripts/verify/verify-stage8-f0e-agent-runtime-binding-idempotency.py

verify-stage8-f0e-runtime-binding-artifact:
	python3 scripts/verify/verify-stage8-f0e-agent-runtime-binding-idempotency.py --require-artifact

verify-stage8-f0f-flow-agent-setup-error-contract:
	python3 scripts/verify/verify-stage8-f0f-flow-agent-setup-error-contract.py

characterize-stage8-report:
	python3 scripts/characterization/stage8_release_gate_contract.py

characterize-stage8-dry-run:
	./scripts/characterization/run-stage8.sh --dry-run

characterize-stage8-strict:
	./scripts/characterization/run-stage8.sh --strict

test-stage8-admin-ui:
	cd ai-event-gateway-admin-ui && npm run stage8:release-gate

stage8-release-gate:
	$(MAKE) verify-stage8-release-gate
	$(MAKE) verify-stage8-dispatch-authority-unification
	$(MAKE) verify-stage8-f0e-agent-runtime-binding-idempotency
	$(MAKE) verify-stage8-f0f-flow-agent-setup-error-contract
	$(MAKE) characterize-stage8-report
	./scripts/release/stage8-release-gate.sh --strict

.PHONY: verify-stage1-direct-dispatch-no-offer phase1-direct-dispatch-no-offer
verify-stage1-direct-dispatch-no-offer:
	python3 scripts/verify/verify-stage1-direct-dispatch-no-offer.py

phase1-direct-dispatch-no-offer: verify-stage1-direct-dispatch-no-offer
	@echo "Stage 1 direct dispatch no-offer contract verified."

.PHONY: verify-stage2-agent-direct-readiness-no-legacy-blockers phase2-agent-direct-readiness-no-legacy-blockers
verify-stage2-agent-direct-readiness-no-legacy-blockers:
	python3 scripts/verify/verify-stage2-agent-direct-readiness-no-legacy-blockers.py

phase2-agent-direct-readiness-no-legacy-blockers: verify-stage2-agent-direct-readiness-no-legacy-blockers
	@echo "Stage 2 agent direct readiness without legacy blockers contract verified."

.PHONY: verify-stage3-dispatch-governance-removed-from-standard-path phase3-dispatch-governance-removed-from-standard-path
verify-stage3-dispatch-governance-removed-from-standard-path:
	python3 scripts/verify/verify-stage3-dispatch-governance-removed-from-standard-path.py

phase3-dispatch-governance-removed-from-standard-path: verify-stage3-dispatch-governance-removed-from-standard-path
	@echo "Phase 3 dispatch governance removed from standard path verified."

.PHONY: verify-stage4-dispatch-flow-crud-runtime-source-of-truth phase4-dispatch-flow-crud-runtime-source-of-truth
verify-stage4-dispatch-flow-crud-runtime-source-of-truth:
	python3 scripts/verify/verify-stage4-dispatch-flow-crud-runtime-source-of-truth.py

phase4-dispatch-flow-crud-runtime-source-of-truth: verify-stage4-dispatch-flow-crud-runtime-source-of-truth
	@echo "Phase 4 Dispatch Flow CRUD/runtime source-of-truth contract verified."

.PHONY: verify-stage5-capability-task-scope-cleanup phase5-capability-task-scope-cleanup
verify-stage5-capability-task-scope-cleanup:
	python3 scripts/verify/verify-stage5-capability-task-scope-cleanup.py

phase5-capability-task-scope-cleanup: verify-stage5-capability-task-scope-cleanup
	@echo "Phase 5 Capability / Task Scope cleanup contract verified."

.PHONY: verify-stage6-source-system-master-data phase6-source-system-master-data
verify-stage6-source-system-master-data:
	python3 scripts/verify/verify-stage6-source-system-master-data.py

phase6-source-system-master-data: verify-stage6-source-system-master-data
	@echo "Phase 6 Source System master-data contract verified."

.PHONY: verify-stage7-admin-permission-standard-workflow phase7-admin-permission-standard-workflow
verify-stage7-admin-permission-standard-workflow:
	python3 scripts/verify/verify-stage7-admin-permission-standard-workflow.py

phase7-admin-permission-standard-workflow: verify-stage7-admin-permission-standard-workflow
	@echo "Phase 7 admin permission standard workflow contract verified."

.PHONY: verify-stage8-task-runtime-decision-chain phase8-task-runtime-decision-chain
verify-stage8-task-runtime-decision-chain:
	python3 scripts/verify/verify-stage8-task-runtime-decision-chain.py

phase8-task-runtime-decision-chain: verify-stage8-task-runtime-decision-chain
	@echo "Phase 8 Task Runtime Decision Chain contract verified."

verify-stage9-browser-e2e:
	python3 scripts/verify/verify-stage9-browser-e2e.py

test-stage9-browser-e2e:
	cd ai-event-gateway-admin-ui && npm run test:e2e:stage9

phase9-browser-e2e: verify-stage9-browser-e2e test-stage9-browser-e2e


.PHONY: verify-stage10-clean-baseline phase10-clean-baseline
verify-stage10-clean-baseline:
	python3 scripts/verify/verify-stage10-clean-baseline.py

phase10-clean-baseline: verify-stage10-clean-baseline

.PHONY: verify-stage11-legacy-code-purge phase11-legacy-code-purge
verify-stage11-legacy-code-purge:
	python3 scripts/verify/verify-stage11-legacy-code-purge.py

phase11-legacy-code-purge: verify-stage11-legacy-code-purge
	@echo "Phase 11 legacy code purge contract verified."

.PHONY: verify-stage12-compile-integration-repair-gate phase12-compile-integration-repair-gate phase12-live-integration-gate
verify-stage12-compile-integration-repair-gate:
	python3 scripts/verify/verify-stage12-compile-integration-repair-gate.py

phase12-compile-integration-repair-gate: verify-stage12-compile-integration-repair-gate
	@echo "Phase 12 compile/integration repair contract verified. Run phase12-live-integration-gate in a full JDK25/Maven/Docker/live stack environment."

phase12-live-integration-gate:
	./scripts/verify/phase12-live-integration-gate.sh

.PHONY: verify-stage13-live-stack-golden-path phase13-live-stack-golden-path-gate
verify-stage13-live-stack-golden-path:
	python3 scripts/verify/verify-stage13-live-stack-golden-path.py

phase13-live-stack-golden-path-gate: verify-stage13-live-stack-golden-path
	./scripts/verify/phase13-live-stack-golden-path-gate.sh

.PHONY: verify-stage14-agent-control-compile-repair phase14-agent-control-compile-repair
verify-stage14-agent-control-compile-repair:
	python3 scripts/verify/verify-stage14-agent-control-compile-repair.py

phase14-agent-control-compile-repair: verify-stage14-agent-control-compile-repair
	@echo "Phase 14 agent-control compile repair contract verified. Rerun Maven agent-control compile in the full JDK25/Maven environment."

.PHONY: verify-stage15-agent-control-testcompile-repair phase15-agent-control-testcompile-repair
verify-stage15-agent-control-testcompile-repair:
	python3 scripts/verify/verify-stage15-agent-control-testcompile-repair.py

phase15-agent-control-testcompile-repair: verify-stage15-agent-control-testcompile-repair

.PHONY: verify-stage16-control-plane-task-orchestration-compile-repair phase16-control-plane-task-orchestration-compile-repair
verify-stage16-control-plane-task-orchestration-compile-repair:
	python3 scripts/verify/verify-stage16-control-plane-task-orchestration-compile-repair.py

phase16-control-plane-task-orchestration-compile-repair: verify-stage16-control-plane-task-orchestration-compile-repair
	@echo "Phase 16 control-plane/task-orchestration compile repair contract verified. Rerun Maven compile in the full JDK25/Maven environment."

.PHONY: verify-stage17-live-schema-baseline-repair phase17-live-schema-baseline-repair
verify-stage17-live-schema-baseline-repair:
	python3 scripts/verify/verify-stage17-live-schema-baseline-repair.py

phase17-live-schema-baseline-repair: verify-stage17-live-schema-baseline-repair
	@echo "Phase 17 live schema baseline repair contract verified. Rebuild Core and reset DB before rerunning cluster bootstrap."

.PHONY: verify-stage18-incident-lifecycle-schema-repair phase18-incident-lifecycle-schema-repair
verify-stage18-incident-lifecycle-schema-repair:
	python3 scripts/verify/verify-stage18-incident-lifecycle-schema-repair.py

phase18-incident-lifecycle-schema-repair: verify-stage18-incident-lifecycle-schema-repair
	@echo "Phase 18 incident lifecycle schema repair contract verified. Reset DB or run the dev hotfix SQL before rerunning cluster bootstrap."

.PHONY: verify-stage19-agent-enrollment-diagnostic-logging phase19-agent-enrollment-diagnostic-logging
verify-stage19-agent-enrollment-diagnostic-logging:
	python3 scripts/verify/verify-stage19-agent-enrollment-diagnostic-logging.py

phase19-agent-enrollment-diagnostic-logging: verify-stage19-agent-enrollment-diagnostic-logging
	@echo "Phase 19 agent enrollment diagnostic logging contract verified. Rerun bootstrap and search Core logs by correlationId/requestId."

.PHONY: verify-stage20-gateway-node-heartbeat-schema-repair phase20-gateway-node-heartbeat-schema-repair
verify-stage20-gateway-node-heartbeat-schema-repair:
	python3 scripts/verify/verify-stage20-gateway-node-heartbeat-schema-repair.py

phase20-gateway-node-heartbeat-schema-repair: verify-stage20-gateway-node-heartbeat-schema-repair
	@echo "Phase 20 gateway node heartbeat schema repair contract verified. Reset DB or run the dev hotfix SQL before rerunning cluster bootstrap."

.PHONY: verify-stage21-flyway-migration-diagnostics phase21-flyway-migration-diagnostics
verify-stage21-flyway-migration-diagnostics:
	python3 scripts/verify/verify-stage21-flyway-migration-diagnostics.py

phase21-flyway-migration-diagnostics: verify-stage21-flyway-migration-diagnostics
	@echo "Phase 21 Flyway migration diagnostics contract verified."

.PHONY: verify-stage22-flyway-macos-metadata-sanitization phase22-flyway-macos-metadata-sanitization
verify-stage22-flyway-macos-metadata-sanitization:
	python3 scripts/verify/verify-stage22-flyway-macos-metadata-sanitization.py

phase22-flyway-macos-metadata-sanitization: verify-stage22-flyway-macos-metadata-sanitization
	@echo "Phase 22 Flyway macOS metadata sanitization contract verified. Reseed migration volumes with make down-v && make cd-local."

.PHONY: verify-stage23-flyway-pending-validate-repair phase23-flyway-pending-validate-repair
verify-stage23-flyway-pending-validate-repair:
	python3 scripts/verify/verify-stage23-flyway-pending-validate-repair.py

phase23-flyway-pending-validate-repair: verify-stage23-flyway-pending-validate-repair
	@echo "Phase 23 Flyway pending validate repair contract verified. Fresh DB pending migrations now proceed to migrate."

.PHONY: verify-stage24-agent-governance-audit-schema-repair phase24-agent-governance-audit-schema-repair
verify-stage24-agent-governance-audit-schema-repair:
	python3 scripts/verify/verify-stage24-agent-governance-audit-schema-repair.py

phase24-agent-governance-audit-schema-repair: verify-stage24-agent-governance-audit-schema-repair
	@echo "Phase 24 Agent governance audit schema repair contract verified. Reset DB or apply the dev hotfix SQL before rerunning Agent bootstrap."

.PHONY: verify-stage25-runtime-resource-upsert-conflict-repair phase25-runtime-resource-upsert-conflict-repair
verify-stage25-runtime-resource-upsert-conflict-repair:
	python3 scripts/verify/verify-stage25-runtime-resource-upsert-conflict-repair.py

phase25-runtime-resource-upsert-conflict-repair: verify-stage25-runtime-resource-upsert-conflict-repair
	@echo "Phase 25 runtime resource upsert conflict repair contract verified. Reset DB or apply the dev hotfix SQL before rerunning Agent bootstrap."

.PHONY: verify-stage26-runtime-binding-post-contract-repair phase26-runtime-binding-post-contract-repair
verify-stage26-runtime-binding-post-contract-repair:
	python3 scripts/verify/verify-stage26-runtime-binding-post-contract-repair.py

phase26-runtime-binding-post-contract-repair: verify-stage26-runtime-binding-post-contract-repair
	@echo "Phase 26 runtime binding POST contract repair verified. Rebuild Core before rerunning Agent bootstrap."

.PHONY: verify-stage27-event-intake-runtime-schema-repair phase27-event-intake-runtime-schema-repair
verify-stage27-event-intake-runtime-schema-repair:
	python3 scripts/verify/verify-stage27-event-intake-runtime-schema-repair.py

phase27-event-intake-runtime-schema-repair: verify-stage27-event-intake-runtime-schema-repair
	@echo "Phase 27 event intake/runtime schema repair verified. Reset DB or apply the dev hotfix SQL before rerunning CMS intake and runtime UI checks."

.PHONY: verify-stage28-mybatis-dao-schema-contract phase28-mybatis-dao-schema-contract
verify-stage28-mybatis-dao-schema-contract:
	python3 scripts/verify/verify-stage28-mybatis-dao-schema-contract.py

phase28-mybatis-dao-schema-contract: verify-stage28-mybatis-dao-schema-contract
	@echo "Phase 28 MyBatis DAO schema contract verified. Reset DB or apply the dev hotfix SQL before rerunning event intake."

.PHONY: verify-stage29-json-null-safe-mybatis-contract phase29-json-null-safe-mybatis-contract
verify-stage29-json-null-safe-mybatis-contract:
	python3 scripts/verify/verify-stage29-json-null-safe-mybatis-contract.py

phase29-json-null-safe-mybatis-contract: verify-stage29-json-null-safe-mybatis-contract
	@echo "Phase 29 JSON null-safe MyBatis contract verified. Rebuild Core before rerunning event intake."

.PHONY: verify-stage30-dispatch-cutover-jdbc-schema-contract phase30-dispatch-cutover-jdbc-schema-contract
verify-stage30-dispatch-cutover-jdbc-schema-contract:
	python3 scripts/verify/verify-stage30-dispatch-cutover-jdbc-schema-contract.py

phase30-dispatch-cutover-jdbc-schema-contract: verify-stage30-dispatch-cutover-jdbc-schema-contract
	@echo "Phase 30 dispatch cutover JDBC schema contract verified. Reset DB or apply the dev hotfix SQL before rerunning event intake."

.PHONY: verify-stage31-round-robin-minimum-score-repair phase31-round-robin-minimum-score-repair
verify-stage31-round-robin-minimum-score-repair:
	python3 scripts/verify/verify-stage31-round-robin-minimum-score-repair.py

phase31-round-robin-minimum-score-repair: verify-stage31-round-robin-minimum-score-repair
	@echo "Phase 31 round-robin minimum-score repair contract verified."

.PHONY: verify-phase32a-source-flow-contract phase32-a
verify-phase32a-source-flow-contract:
	python3 scripts/verify/verify-phase32a-source-flow-contract.py

phase32-a: verify-phase32a-source-flow-contract
	@echo "Phase 32-A source flow / agent pool contract verified."

.PHONY: verify-phase32b-agent-pool-persistence phase32-b
verify-phase32b-agent-pool-persistence:
	python3 scripts/verify/verify-phase32b-agent-pool-persistence.py

phase32-b: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence
	@echo "Phase 32-B Agent Pool persistence contract verified."

.PHONY: verify-phase32c-event-intake-relaxation phase32-c
verify-phase32c-event-intake-relaxation:
	python3 scripts/verify/verify-phase32c-event-intake-relaxation.py

phase32-c: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence verify-phase32c-event-intake-relaxation
	@echo "Phase 32-C event intake relaxation contract verified."


.PHONY: verify-phase32d-source-flow-pool-routing phase32-d
verify-phase32d-source-flow-pool-routing:
	python3 scripts/verify/verify-phase32d-source-flow-pool-routing.py

phase32-d: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence verify-phase32c-event-intake-relaxation verify-phase32d-source-flow-pool-routing
	@echo "Phase 32-D Source Flow default Pool routing contract verified."


.PHONY: verify-phase32e-triage-classification-resolution phase32-e
verify-phase32e-triage-classification-resolution:
	python3 scripts/verify/verify-phase32e-triage-classification-resolution.py

phase32-e: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence verify-phase32c-event-intake-relaxation verify-phase32d-source-flow-pool-routing verify-phase32e-triage-classification-resolution
	@echo "Phase 32-E triage classification / resolution gate passed."

.PHONY: verify-phase32f-task-detail-a2a-evidence-chain phase32-f
verify-phase32f-task-detail-a2a-evidence-chain:
	python3 scripts/verify/verify-phase32f-task-detail-a2a-evidence-chain.py

phase32-f: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence verify-phase32c-event-intake-relaxation verify-phase32d-source-flow-pool-routing verify-phase32e-triage-classification-resolution verify-phase32f-task-detail-a2a-evidence-chain
	@echo "Phase 32-F Task Detail A2A evidence chain gate passed."

.PHONY: verify-phase32g-source-flow-agent-pool-admin-ui phase32-g
verify-phase32g-source-flow-agent-pool-admin-ui:
	python3 scripts/verify/verify-phase32g-source-flow-agent-pool-admin-ui.py

phase32-g: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence verify-phase32c-event-intake-relaxation verify-phase32d-source-flow-pool-routing verify-phase32e-triage-classification-resolution verify-phase32f-task-detail-a2a-evidence-chain verify-phase32g-source-flow-agent-pool-admin-ui
	@echo "Phase 32-G Source Flow / Agent Pool admin UI gate passed."


.PHONY: verify-phase32h-pool-first-diagnostics phase32-h
verify-phase32h-pool-first-diagnostics:
	python3 scripts/verify/verify-phase32h-pool-first-diagnostics.py

phase32-h: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence verify-phase32c-event-intake-relaxation verify-phase32d-source-flow-pool-routing verify-phase32e-triage-classification-resolution verify-phase32f-task-detail-a2a-evidence-chain verify-phase32g-source-flow-agent-pool-admin-ui verify-phase32h-pool-first-diagnostics
	@echo "Phase 32-H Pool-first diagnostics gate passed."

.PHONY: verify-phase32i-source-system-only-golden-path phase32-i-acceptance-dry-run phase32-i-live phase32-i phase32-release-gate verify-phase32
verify-phase32i-source-system-only-golden-path:
	python3 scripts/verify/verify-phase32i-source-system-only-golden-path.py

phase32-i-acceptance-dry-run:
	node scripts/acceptance/phase32i-source-system-only-golden-path.mjs --dry-run --negative

phase32-i-live:
	node scripts/acceptance/phase32i-source-system-only-golden-path.mjs --negative

phase32-i: verify-phase32a-source-flow-contract verify-phase32b-agent-pool-persistence verify-phase32c-event-intake-relaxation verify-phase32d-source-flow-pool-routing verify-phase32e-triage-classification-resolution verify-phase32f-task-detail-a2a-evidence-chain verify-phase32g-source-flow-agent-pool-admin-ui verify-phase32h-pool-first-diagnostics verify-phase32i-source-system-only-golden-path phase32-i-acceptance-dry-run
	@echo "Phase 32-I SourceSystem-only golden path release gate passed."

phase32-release-gate: verify-current-app-contract phase32-i-acceptance-dry-run
	@echo "Phase 32 current code/config release gate passed."

verify-phase32: verify-current-app-contract phase32-i-acceptance-dry-run
	@echo "Phase 32 current code/config verification is included in make verify."
