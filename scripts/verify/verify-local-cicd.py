#!/usr/bin/env python3
"""Static verifier for local Docker CI/CD automation through P14.

Local CI validates executable development/runtime automation only. It must not
fail because markdown documentation or phase delivery summaries are missing.
"""
from __future__ import annotations

import re
import stat
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_RUNTIME_FILES = [
    "Makefile",
    "deploy/docker-compose.ci.yml",
    "deploy/env/.env.local.ci",
    "scripts/ci/local-ci.sh",
    "scripts/ci/local-cd.sh",
    "scripts/ci/local-clean.sh",
    "scripts/ci/source-clean-check.sh",
    "scripts/ci/admin-ui-clean-generated.sh",
    "scripts/ci/local-smoke.sh",
    "scripts/ci/local-admin-ui-host.sh",
    "scripts/ci/env-utils.sh",
    "scripts/ci/prepare-admin-ui-runtime.sh",
    "scripts/ci/local-diagnose.sh",
    "scripts/ci/local-report.sh",
    "scripts/ci/local-port-check.sh",
    "scripts/ci/local-open.sh",
    "scripts/ci/local-status.sh",
    "scripts/observability/otlp-export-smoke.sh",
    "scripts/verify/verify-p1b-otlp-collector-config.py",
    "scripts/verify/verify-p3a-spring-managed-context-propagation.py",
    "deploy/observability/otel-collector-config.yml",
    "scripts/verify/verify-local-cicd.py",
    "scripts/acceptance/api-runtime-smoke.mjs",
    "scripts/acceptance/runtime-lifecycle-e2e.sh",
    "scripts/acceptance/agent-governance-lifecycle-e2e.mjs",
    "ai-event-gateway-core/scripts/e2e/run_core_netty_agent_e2e.py",
    "ai-event-gateway-admin-ui/scripts/route-smoke.mjs",
    "ai-event-gateway-admin-ui/next.config.runtime.mjs",
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/P25RepositoryDbContainerSupport.java",
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/AgentGovernanceRepositoryDbHardeningContainerTest.java",
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/DispatchRequestRepositoryDbHardeningContainerTest.java",
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/TaskCallbackRepositoryDbHardeningContainerTest.java",
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/RemediationWorkflowRepositoryDbHardeningContainerTest.java",
    "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/SkillRegistryRepositoryDbHardeningContainerTest.java",
]

REQUIRED_MAKE_TARGETS = [
    "ci-fast",
    "ci-pr",
    "ci-release",
    "smoke",
    "smoke-otlp",
    "verify-p1b-otlp-collector",
    "verify-p3a-spring-managed-context-propagation",
    "test-admin-strict",
    "test-source-clean",
    "ci-local",
    "ci-local-teardown",
    "cd-local",
    "ci-port-check",
    "ci-diagnose",
    "ci-report",
    "ci-smoke",
    "ci-open",
    "ci-ps",
    "ci-logs",
    "ci-down",
    "ci-down-v",
    "clean-ci",
]

REQUIRED_CI_STAGES = [
    "Stage 0 - Preflight",
    "Stage 1 - Static code verify / repository hygiene",
    "Stage 2 - Core Java tests by module group",
    "Stage 3 - Netty Java tests",
    "Stage 4 - Admin UI checks",
    "Stage 5 - Package executable jars for shared Java 25 runtime",
    "Stage 6 - Prepare shared runtime images",
    "Stage 7 - Compose up shared-runtime stack",
    "Stage 8 - Smoke test",
    "Stage 8.1 - OTLP export smoke",
]

COMPOSE_SERVICES = ["postgres", "core-db-migrate", "redis", "otel-collector", "core", "netty", "adapter-worker", "admin-ui"]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def warn(message: str) -> None:
    print(f"[WARN] {message}", file=sys.stderr)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.exists():
        fail(f"Missing required file: {relative}")
    if not path.is_file():
        fail(f"Required path is not a file: {relative}")
    return path


def assert_contains(path: Path, needles: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(ROOT)} does not contain required text: {needle}")


def assert_executable(path: Path) -> None:
    mode = path.stat().st_mode
    if not (mode & stat.S_IXUSR):
        fail(f"Script is not executable: {path.relative_to(ROOT)}")


def assert_not_contains(path: Path, needles: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle in text:
            fail(f"{path.relative_to(ROOT)} contains forbidden text: {needle}")


def main() -> int:
    for relative in REQUIRED_RUNTIME_FILES:
        require_file(relative)

    makefile = require_file("Makefile")
    assert_contains(makefile, [f"{target}:" for target in REQUIRED_MAKE_TARGETS])
    assert_contains(makefile, [
        "local-status.sh",
        "local-open.sh",
        "local-smoke.sh --project $(PROJECT_NAME)-ci",
        "local-report.sh",
        "scripts/observability/otlp-export-smoke.sh",
        "local-diagnose.sh",
        "local-port-check.sh",
        "source-clean-check.sh",
        "admin-ui-clean-generated.sh",
        "test-admin-strict",
        "NEXT_DIST_DIR=.next-ci",
        "RepositoryDbHardeningContainerTest",
        "-Dgroups=container",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-DfailIfNoTests=false",
        "test-source-clean",
        "ci-pr",
        "ci-release",
        "install-ci-workflows",
        "$(MAKE) install-ci-workflows",
        "GATEWAY_AGENT_AUTHORIZATION_ENABLED=true RUN_RUNTIME_LIFECYCLE_E2E=true $(MAKE) ci-local-teardown",
    ])
    assert_not_contains(makefile, [
        "api-envelope-acceptance:",
        "test-contract:",
        "test-runtime-smoke:",
        "test-runtime-lifecycle:",
        "test-repository-db:",
        "make api-envelope-acceptance",
        "make test-contract",
        "make test-runtime-smoke",
        "make test-runtime-lifecycle",
        "make test-repository-db",
    ])

    local_ci = require_file("scripts/ci/local-ci.sh")
    local_cd = require_file("scripts/ci/local-cd.sh")
    local_smoke = require_file("scripts/ci/local-smoke.sh")
    local_admin_host = require_file("scripts/ci/local-admin-ui-host.sh")
    env_utils = require_file("scripts/ci/env-utils.sh")
    prepare_admin_runtime = require_file("scripts/ci/prepare-admin-ui-runtime.sh")
    local_status = require_file("scripts/ci/local-status.sh")
    local_open = require_file("scripts/ci/local-open.sh")
    local_port_check = require_file("scripts/ci/local-port-check.sh")
    local_report = require_file("scripts/ci/local-report.sh")
    local_diagnose = require_file("scripts/ci/local-diagnose.sh")
    admin_next_config = require_file("ai-event-gateway-admin-ui/next.config.ts")
    admin_package = require_file("ai-event-gateway-admin-ui/package.json")
    admin_npmrc = require_file("ai-event-gateway-admin-ui/.npmrc")
    admin_lock = require_file("ai-event-gateway-admin-ui/package-lock.json")
    local_clean = require_file("scripts/ci/local-clean.sh")
    source_clean_check = require_file("scripts/ci/source-clean-check.sh")
    release_package = require_file("scripts/release/build-release-package.sh")
    admin_ui_clean_generated = require_file("scripts/ci/admin-ui-clean-generated.sh")
    api_runtime_smoke = require_file("scripts/acceptance/api-runtime-smoke.mjs")
    runtime_lifecycle_e2e = require_file("scripts/acceptance/runtime-lifecycle-e2e.sh")
    agent_governance_lifecycle_e2e = require_file("scripts/acceptance/agent-governance-lifecycle-e2e.mjs")
    i6_runtime_e2e = require_file("ai-event-gateway-core/scripts/e2e/run_core_netty_agent_e2e.py")
    admin_route_smoke = require_file("ai-event-gateway-admin-ui/scripts/route-smoke.mjs")
    admin_run_next_build = require_file("ai-event-gateway-admin-ui/scripts/run-next-build.mjs")
    p14_scripts = [local_status, local_open, local_port_check, local_report, local_diagnose]
    for script in [local_ci, local_cd, local_smoke, local_admin_host, env_utils, prepare_admin_runtime, local_clean, source_clean_check, admin_ui_clean_generated, runtime_lifecycle_e2e, *p14_scripts]:
        assert_executable(script)
        assert_contains(script, ["set -euo pipefail"])
    assert_executable(agent_governance_lifecycle_e2e)


    assert_contains(release_package, [
        "ADMIN_UI_RELEASE_DIST_DIR",
        "NEXT_DIST_DIR=",
        "ADMIN_UI_BUILD_PATH",
        "runtime/admin-ui/node_modules",
    ])

    assert_contains(source_clean_check, [
        "Source tree contains generated artifacts or archive metadata",
        "tsconfig.tsbuildinfo",
        "__pycache__",
        "node_modules",
        "Source cleanliness check passed",
        ".next-ci",
        ".next-release",
    ])

    assert_contains(admin_ui_clean_generated, [
        "Admin UI generated artifacts",
        "Stopping CI Admin UI container before host-side cleanup",
        "docker run --rm",
        "alpine:3.20",
        "Unable to remove generated Admin UI artifacts",
        ".next-ci",
        ".next-release",
    ])

    assert_contains(api_runtime_smoke, [
        "Runtime smoke acceptance for high-risk OpenDispatch API surfaces",
        "Core Agent Governance list contract",
        "Core Task Callback metadata contract",
        "Core Admin Task failure queue contract",
        "Netty Admin Runtime snapshot contract",
        "Admin UI proxy Core Agent Governance contract",
        "Admin UI proxy Netty Runtime snapshot contract",
        "isStandardEnvelope",
        "expected HTTP 200",
    ])

    assert_contains(runtime_lifecycle_e2e, [
        "P26 runtime lifecycle E2E",
        "P26_RUNTIME_E2E_SCENARIOS",
        "I6_AGENT_TENANT_ID",
        "I6_EVENT_TENANT_ID",
        "I6_AGENT_SITE_ID",
        "agent-governance-lifecycle-e2e.mjs",
        "run-i7-runtime-gate.sh",
        "P26_RUNTIME_E2E_SKIP_PREFLIGHT_SMOKE",
        "GATEWAY_AGENT_AUTHORIZATION_ENABLED",
        "INCIDENT_ANALYSIS,TASK_EXECUTION,GENERAL_AGENT",
        "I6_REQUIRED_CAPABILITIES",
        "I6_AGENT_HEARTBEAT_INTERVAL_SECONDS",
    ])

    assert_contains(i6_runtime_e2e, [
        "ensure_core_agent_approved",
        "prewarm_core_agent_directory",
        "wait_for_assignable_agent_candidate",
        "/admin/agent-enrollments",
        "/api/agents/register",
        "/api/agents/available",
        "/admin/agents/{urllib.parse.quote(args.agent_id)}/credentials/issue",
        "I6_AGENT_TENANT_ID",
        "I6_EVENT_TENANT_ID",
        "I6_AGENT_SITE_ID",
        "core governance profile ready",
        "agent assignable in Core directory",
        "I6_AGENT_CAPABILITIES",
        "I6_REQUIRED_CAPABILITIES",
    ])

    assert_contains(agent_governance_lifecycle_e2e, [
        "P26 Agent Governance runtime lifecycle E2E",
        "disable Core Agent profile and force runtime disconnect",
        "Netty TCP Agent client",
        "transportStatus === 'CONNECTED'",
        "Agent governance runtime lifecycle E2E passed",
    ])

    assert_contains(admin_route_smoke, [
        "Admin UI route-level smoke test",
        "ADMIN_ROUTE_SMOKE_PATHS",
        "Admin UI route smoke against",
        "text/html",
        "Admin UI route smoke completed",
    ])

    p25_support = require_file("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/P25RepositoryDbContainerSupport.java")
    p25_agent = require_file("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/AgentGovernanceRepositoryDbHardeningContainerTest.java")
    p25_dispatch = require_file("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/DispatchRequestRepositoryDbHardeningContainerTest.java")
    p25_callback = require_file("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/TaskCallbackRepositoryDbHardeningContainerTest.java")
    p25_remediation = require_file("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/RemediationWorkflowRepositoryDbHardeningContainerTest.java")
    p25_skill = require_file("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/SkillRegistryRepositoryDbHardeningContainerTest.java")
    redis_dedup_atomic = require_file("ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/RedisDedupAtomicContainerTest.java")
    assert_contains(p25_support, [
        "PostgreSQLContainer<>(\"postgres:18-alpine\")",
        "Flyway.configure()",
        "MybatisContainerTestSupport",
        "agentGovernanceRepository()",
        "dispatchRequestRepository()",
        "taskCallbackRepository()",
        "remediationWorkflowStore()",
        "skillRegistryRepository()",
    ])
    assert_contains(p25_agent, ["AgentGovernanceRepositoryDbHardeningContainerTest", "findActiveCredentialByTokenHash", "replaceCapabilities", "saveSecurityEnforcementPolicy"])
    assert_contains(p25_dispatch, ["DispatchRequestRepositoryDbHardeningContainerTest", "claimExecutable", "transitionStatus", "PersistenceWriteOutcome.CONFLICT"])
    assert_contains(p25_callback, ["TaskCallbackRepositoryDbHardeningContainerTest", "tryReserve", "ReplayMetadata", "findByTaskId"])
    assert_contains(p25_remediation, ["RemediationWorkflowRepositoryDbHardeningContainerTest", "acquireWorkflowExecutionLease", "insertActionExecutionIfAbsent", "completeActionExecutionIfRunning"])
    assert_contains(p25_skill, ["SkillRegistryRepositoryDbHardeningContainerTest", "upsertVersion", "upsertApprovalPolicy", "replaceDependencyEdges"])
    assert_contains(redis_dedup_atomic, [
        "Wait.forListeningPort()",
        "withStartupTimeout(Duration.ofSeconds(60))",
        "awaitRedisReady",
        "assumeTrue",
        "commandTimeout(Duration.ofSeconds(10))",
        "setValidateConnection(true)",
    ])

    assert_contains(admin_package, [
        "smoke:e2e",
        "smoke:routes",
        "route-smoke.mjs",
    ])

    assert_contains(local_clean, [
        "Cleaning Maven modules through root aggregator",
        "mvn -q -f pom.xml clean",
        "ai-event-gateway-admin-ui",
        "node_modules",
        "admin-ui-clean-generated.sh",
        "docker compose -p",
        "down --remove-orphans",
        "Local CI, Maven, and Admin UI generated artifacts cleaned.",
    ])

    assert_contains(env_utils, [
        "load_dotenv_file",
        "without shell-evaluating",
        'export "${key}=${value}"',
    ])
    for env_loading_script in [local_smoke, local_admin_host]:
        text = env_loading_script.read_text(encoding="utf-8")
        if 'source "$ENV_FILE"' in text or "source '$ENV_FILE'" in text or "set -a" in text:
            fail(f"{env_loading_script.relative_to(ROOT)} must not shell-source .env files; use load_dotenv_file")
        if 'load_dotenv_file "$ENV_FILE"' not in text:
            fail(f"{env_loading_script.relative_to(ROOT)} must load .env through load_dotenv_file")
        if not (
            'source "${ROOT_DIR}/scripts/ci/env-utils.sh"' in text
            or 'source "${ENV_UTILS_FILE}"' in text
        ):
            fail(f"{env_loading_script.relative_to(ROOT)} must source env-utils.sh through an explicit resolved path")

    # P15.1 made local-smoke.sh usable both from the source repository and from
    # release packages where it is copied under bin/.  The local-CI verifier must
    # validate the behavior contract, not a single hard-coded source path string.

    assert_contains(local_smoke, [
        "SCRIPT_DIR=",
        "ENV_UTILS_FILE=",
        "Release package layout",
        "Source repository layout",
        "--self-check",
        "OpenDispatch smoke self-check passed.",
    ])

    next_config_text = admin_next_config.read_text(encoding="utf-8")
    if "async rewrites" in next_config_text or "destination:" in next_config_text or "localhost:18081" in next_config_text:
        fail("Admin UI must not use Next build-time rewrites for backend proxying; use App Router proxy routes with runtime env")
    assert_contains(admin_next_config, [
        "headers()",
        "securityHeaders",
        "NEXT_DIST_DIR",
        "distDir",
    ])

    assert_contains(local_ci, REQUIRED_CI_STAGES)
    assert_contains(local_ci, [
        "verify-local-cicd.py",
        "mvn -pl task-orchestration -am test",
        "mvn -pl execution-control -am test",
        "mvn -pl control-plane-app,adapter-worker-app -am test",
        "npm run test:normalizers",
        "npm run typecheck",
        "npm run lint",
        "npm run test:api-envelope",
        "Package executable jars for shared Java 25 runtime",
        "Prepare shared runtime images",
        "CORE_JAR=",
        "NETTY_JAR=",
        "ADAPTER_WORKER_JAR=",
        "compose_ci down --remove-orphans",
        "seed_ci_runtime_volumes",
        "CI_DB_MIGRATION_VOLUME",
        "CI_CORE_RUNTIME_VOLUME",
        "CI_NETTY_RUNTIME_VOLUME",
        "CI_ADAPTER_WORKER_RUNTIME_VOLUME",
        "CI_ADMIN_UI_RUNTIME_VOLUME",
        "CI_OTEL_COLLECTOR_CONFIG_VOLUME",
        "CI_FLYWAY_DIAGNOSTICS_VOLUME",
        "CI_CORE_LOGS_VOLUME",
        "CI_NETTY_LOGS_VOLUME",
        "CI_ADAPTER_WORKER_LOGS_VOLUME",
        "CI_ADMIN_UI_LOGS_VOLUME",
        "CI_MOCK_AGENT_E2E_VOLUME",
        "flyway-migrate-with-diagnostics.sh",
        "otel-collector-config.yml",
        "CI_VOLUME_SEED_IMAGE",
        "docker volume create",
        "docker run --rm -i -v",
        "compose_ci up -d --remove-orphans --force-recreate",
        "admin-ui",
        "ADMIN_UI_RUNTIME=node-container",
        "NODE_RUNTIME_IMAGE",
        "prepare-admin-ui-runtime.sh",
        "ADMIN_UI_NEXT_DIST_DIR",
        "NEXT_DIST_DIR=",
        "--build-dir",
        'KEEP_STACK="${KEEP_STACK:-true}"',
        "--teardown|--no-keep-stack",
        "Stage 10 - Keep local stack running",
        "Stage 10 - Teardown requested",
        "cleaned-tsbuildinfo.txt",
        "cleaned-macosx-dirs.txt",
        "cleaned-appledouble-files.txt",
        "local-report.sh",
        "scripts/observability/otlp-export-smoke.sh",
        "make ci-ps",
        "make ci-report",
        "make ci-diagnose",
    ])
    local_ci_text = local_ci.read_text(encoding="utf-8")
    if "docker build" in local_ci_text:
        fail("local-ci.sh must not build per-application Docker images")
    local_cd_text = local_cd.read_text(encoding="utf-8")
    if "docker build" in local_cd_text:
        fail("local-cd.sh must not build per-application Docker images")
    assert_contains(local_cd, ["prepare-admin-ui-runtime.sh", "LOCAL_OTEL_COLLECTOR_CONFIG_VOLUME", "LOCAL_FLYWAY_DIAGNOSTICS_VOLUME", "LOCAL_CORE_LOGS_VOLUME", "LOCAL_NETTY_LOGS_VOLUME", "LOCAL_ADMIN_UI_LOGS_VOLUME", "flyway-migrate-with-diagnostics.sh", "otel-collector-config.yml"])
    assert_contains(prepare_admin_runtime, [
        "ADMIN_UI_BUILD_DIR",
        "--build-dir",
        "runtime/admin-ui",
        "cp -a",
        "next.config.runtime.mjs",
        "node_modules-prod.tar.gz",
        "opendispatch-admin-ui-runtime-entrypoint.sh",
        "${RUNTIME_DIR}/node_modules",
        "nested mount target",
    ])
    forbidden_hard_fail_phrases = [
        "Release/build artifact pollution found",
        "Run make clean-artifacts",
        "Archive metadata pollution found",
    ]
    if "compose_ci down" not in local_ci_text:
        fail("local-ci.sh must still provide explicit teardown path")
    if "./scripts/ci/local-ci.sh --teardown" not in makefile.read_text(encoding="utf-8"):
        fail("Makefile must provide ci-local-teardown for one-shot cleanup")
    for phrase in forbidden_hard_fail_phrases:
        if phrase in local_ci_text:
            fail(f"local-ci.sh must not hard-fail local CI for generated documentation/cache artifacts: {phrase}")
    assert_contains(admin_run_next_build, [
        "NEXT_DIST_DIR",
        "buildDir",
        "CI/CD should use NEXT_DIST_DIR=.next-ci",
    ])

    assert_contains(admin_package, [
        '"test:normalizers"',
        "tsx --import ./tests/node-test-setup.ts --test tests/*.test.ts",
    ])
    assert_contains(admin_npmrc, [
        "registry=https://registry.npmjs.org/",
        "replace-registry-host=always",
    ])
    lock_text = admin_lock.read_text(encoding="utf-8", errors="ignore")
    forbidden_registry_tokens = [
        "packages.applied-caas-gateway",
        "internal.api.openai.org",
        "artifactory/api/npm",
    ]
    for token in forbidden_registry_tokens:
        if token in lock_text:
            fail(f"Admin UI package-lock.json must not reference internal npm registry: {token}")

    test_setup = require_file("ai-event-gateway-admin-ui/tests/node-test-setup.ts")
    assert_contains(test_setup, [
        "from 'node:crypto'",
        "globalThis",
        "getRandomValues",
    ])

    test_dir = ROOT / "ai-event-gateway-admin-ui" / "tests"
    for test_file in sorted(test_dir.glob("*.test.ts")):
        test_text = test_file.read_text(encoding="utf-8", errors="ignore")
        rel = test_file.relative_to(ROOT)
        if "expect(" in test_text:
            fail(f"{rel} uses Jest-style expect(); use node:assert/strict for Node test runner compatibility")
        uses_node_test_globals = re.search(r"\b(describe|it)\s*\(", test_text)
        imports_node_test = "from 'node:test'" in test_text or 'from "node:test"' in test_text
        if uses_node_test_globals and not imports_node_test:
            fail(f"{rel} uses describe()/it() without importing from node:test")


    production_node_crypto_hits: list[str] = []
    for path in (ROOT / "ai-event-gateway-admin-ui").glob("**/*"):
        if not path.is_file():
            continue
        if "node_modules" in path.parts or ".next" in path.parts or path.name.endswith(".test.ts") or path.name == "node-test-setup.ts":
            continue
        if path.suffix not in {".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs"}:
            continue
        production_text = path.read_text(encoding="utf-8", errors="ignore")
        if "node:crypto" in production_text:
            production_node_crypto_hits.append(str(path.relative_to(ROOT)))
    if production_node_crypto_hits:
        fail("Admin UI production/client code must not import node:crypto: " + ", ".join(production_node_crypto_hits[:10]))


    core_dockerfile = require_file("ai-event-gateway-core/control-plane-app/Dockerfile")
    core_dockerfile_text = core_dockerfile.read_text(encoding="utf-8")
    # Guardrail: local CI/CD must run Admin UI as a shared Node runtime container,
    # not as a host process and not as a locally built application image.
    if "host_admin start" in local_ci_text:
        fail("local-ci.sh must not start Admin UI as a host process; use the admin-ui Compose service")

    assert_contains(core_dockerfile, [
        "data_model_jar",
        "BOOT-INF/lib/data-model-",
        'jar tf "/verify/$data_model_jar" | grep "com/opensocket/aievent/database/persistence/incident/dao/IncidentDao.class"',
        'jar tf "/verify/$platform_jar" | grep "mybatis/postgresql/incident/IncidentDao.xml"',
    ])
    stale_platform_dao_check = 'jar tf "/verify/$platform_jar" | grep "com/opensocket/aievent/database/persistence/incident/dao/IncidentDao.class"'
    if stale_platform_dao_check in core_dockerfile_text:
        fail("control-plane Dockerfile must verify DAO classes in data-model jar, not database-platform jar")

    compose = require_file("deploy/docker-compose.ci.yml")
    compose_text = compose.read_text(encoding="utf-8")
    for service in COMPOSE_SERVICES:
        if not re.search(rf"^  {re.escape(service)}:\s*$", compose_text, re.MULTILINE):
            fail(f"docker-compose.ci.yml missing service: {service}")
    assert_contains(compose, [
        "${JAVA25_RUNTIME_IMAGE:-eclipse-temurin:25-jre}",
        "opendispatch-ci-core-runtime:/app:ro",
        "opendispatch-ci-netty-runtime:/app:ro",
        "opendispatch-ci-adapter-worker-runtime:/app:ro",
        "CORE_REMEDIATION_WORKFLOW_METRICS_ENABLED",
        "postgres:18-alpine",
        "redis:8-alpine",
        "flyway/flyway:11-alpine",
        "node:22-bookworm-slim",
        "admin-ui",
        "opendispatch-ci-admin-ui-node-modules",
        "opendispatch-ci-db-migration:/flyway/sql:ro",
        "opendispatch-ci-db-migration-diagnostics:/flyway/diagnostics:ro",
        "opendispatch-ci-admin-ui-runtime:/workspace/admin-ui:ro",
        "opendispatch-ci-core-logs:/logs",
        "opendispatch-ci-netty-logs:/logs",
        "opendispatch-ci-admin-ui-logs:/workspace/logs",
        "opendispatch-ci-mock-agent-e2e:/e2e:ro",
        "./scripts/opendispatch-admin-ui-runtime-entrypoint.sh",
        "ADMIN_UI_ALLOW_NPM_CI: \"false\"",
        "ADMIN_UI_DEPS_ARCHIVE: /workspace/admin-ui/node_modules-prod.tar.gz",
        "CORE_BACKEND_ORIGIN: http://core:18080",
        "NETTY_BACKEND_ORIGIN: http://netty:18081",
        "- /var/lib/postgresql",
        "SPRING_DATA_REDIS_HOST",
        "${REDIS_SERVICE_HOST:-redis}",
        "SPRING_DATA_REDIS_PORT",
        "REDIS_SINGLE_ADDRESS: \"${REDIS_SERVICE_HOST:-redis}:${REDIS_SERVICE_PORT:-6379}\"",
        "DATABASE_PLATFORM_REQUIRE_FLYWAY: \"false\"",
        "MANAGEMENT_HEALTH_SHOW_DETAILS: always",
        "core-db-migrate",
        "service_completed_successfully",
        "name: ${CI_DB_MIGRATION_VOLUME:-opendispatch-ci-db-migration}",
        "name: ${CI_FLYWAY_DIAGNOSTICS_VOLUME:-opendispatch-ci-flyway-diagnostics}",
        "name: ${CI_CORE_RUNTIME_VOLUME:-opendispatch-ci-core-runtime}",
        "name: ${CI_NETTY_RUNTIME_VOLUME:-opendispatch-ci-netty-runtime}",
        "name: ${CI_ADAPTER_WORKER_RUNTIME_VOLUME:-opendispatch-ci-adapter-worker-runtime}",
        "name: ${CI_ADMIN_UI_RUNTIME_VOLUME:-opendispatch-ci-admin-ui-runtime}",
        "name: ${CI_CORE_LOGS_VOLUME:-opendispatch-ci-core-logs}",
        "name: ${CI_NETTY_LOGS_VOLUME:-opendispatch-ci-netty-logs}",
        "name: ${CI_ADAPTER_WORKER_LOGS_VOLUME:-opendispatch-ci-adapter-worker-logs}",
        "name: ${CI_ADMIN_UI_LOGS_VOLUME:-opendispatch-ci-admin-ui-logs}",
        "name: ${CI_MOCK_AGENT_E2E_VOLUME:-opendispatch-ci-mock-agent-e2e}",
        "GATEWAY_AGENT_AUTHORIZATION_ENABLED: ${GATEWAY_AGENT_AUTHORIZATION_ENABLED:-false}",
        "otel/opentelemetry-collector-contrib:0.156.0",
        "profiles: [\"observability-smoke\"]",
        "MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED",
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT",
        "MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED",
    ])
    forbidden_app_image_tokens = [
        "opendispatch/core",
        "opendispatch/netty",
        "opendispatch/admin-ui",
        "ai-event-gateway-core:local",
        "ai-event-gateway-netty:local",
        "ai-event-gateway-admin-ui:local",
    ]
    for token in forbidden_app_image_tokens:
        if token in compose_text:
            fail(f"docker-compose.ci.yml must not reference local application image: {token}")
    if "npm ci --omit=dev" in compose_text or "npm install" in compose_text:
        fail("Admin UI Compose service must not install npm dependencies at container startup; bundle node_modules-prod.tar.gz during prepare-admin-ui-runtime.sh")
    if "build:" in compose_text and re.search(r"^  admin-ui:\s*$", compose_text, re.MULTILINE):
        fail("Admin UI local CI must not build an application image; use the shared Node runtime image")
    if "SPRING_DATA_REDIS_HOST: localhost" in compose_text or "SPRING_DATA_REDIS_HOST: 127.0.0.1" in compose_text:
        fail("Core container must connect to Redis by Compose service name, not localhost")
    if "REDIS_SINGLE_ADDRESS: localhost" in compose_text or "REDIS_SINGLE_ADDRESS: 127.0.0.1" in compose_text:
        fail("SharedUtility Redis address must use Compose service name in local CI")

    if "/var/lib/postgresql/data" in compose_text:
        fail("PostgreSQL 18 local CI must mount /var/lib/postgresql, not /var/lib/postgresql/data")

    env_file = require_file("deploy/env/.env.local.ci")
    assert_contains(env_file, [
        "POSTGRES_DB=ai_event_gateway_ci",
        "JAVA25_RUNTIME_IMAGE=eclipse-temurin:25-jre",
        "NODE_RUNTIME_IMAGE=node:22-bookworm-slim",
        "POSTGRES_IMAGE=postgres:18-alpine",
        "REDIS_IMAGE=redis:8-alpine",
        "REDIS_SERVICE_HOST=redis",
        "REDIS_SERVICE_PORT=6379",
        "REDIS_DATABASE=0",
        "ADMIN_UI_CONTAINER_PORT=3000",
        "FLYWAY_IMAGE=flyway/flyway:11-alpine",
        "CORE_DEFAULT_ROUTING_POLICY=DOMAIN_AWARE",
        "CLUSTER_INTERNAL_TOKEN=local-ci-cluster-token-change-me",
        "NETTY_MACHINE_ADMIN_TOKEN=local-ci-netty-machine-token-change-me",
        "OTEL_COLLECTOR_IMAGE=otel/opentelemetry-collector-contrib:0.156.0",
        "MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=true",
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces",
        "MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true",
    ])


    local_compose = require_file("deploy/docker-compose.local.yml")
    local_compose_text = local_compose.read_text(encoding="utf-8")

    forbidden_host_mounts = [
        "${OPENDISPATCH_LOG_ROOT:-../.local/opendispatch-logs}",
        "${OPENDISPATCH_LOG_ROOT:-../.ci-output/logs}",
        "../ai-event-gateway-core/scripts/e2e:/e2e:ro",
    ]
    combined_compose_text = local_compose_text + "\n" + compose_text
    for token in forbidden_host_mounts:
        if token in combined_compose_text:
            fail(f"local/CI compose must not use Docker Desktop host bind mount for local runtime data: {token}")
    if "/var/lib/postgresql/data" in local_compose_text:
        fail("PostgreSQL 18 local compose must mount /var/lib/postgresql, not /var/lib/postgresql/data")
    assert_contains(local_compose, [
        "postgres:18-alpine",
        "redis:8-alpine",
        "node:22-bookworm-slim",
        "opendispatch-postgres18-data:/var/lib/postgresql",
        "opendispatch-admin-ui-runtime:/workspace/admin-ui:ro",
        "opendispatch-admin-ui-node-modules:/workspace/admin-ui/node_modules",
        "opendispatch-core-logs:/logs",
        "opendispatch-netty-logs:/logs",
        "opendispatch-admin-ui-logs:/workspace/logs",
        "ADMIN_UI_ALLOW_NPM_CI: \"false\"",
        "ADMIN_UI_DEPS_ARCHIVE: /workspace/admin-ui/node_modules-prod.tar.gz",
        "opendispatch-admin-ui-node-modules:",
        "otel/opentelemetry-collector-contrib:0.156.0",
        "opendispatch-adapter-worker-runtime:/app:ro",
        "opendispatch-db-migration-diagnostics:/flyway/diagnostics:ro",
        "name: ${LOCAL_FLYWAY_DIAGNOSTICS_VOLUME:-opendispatch-local-flyway-diagnostics}",
        "name: ${LOCAL_CORE_LOGS_VOLUME:-opendispatch-local-core-logs}",
        "name: ${LOCAL_NETTY_LOGS_VOLUME:-opendispatch-local-netty-logs}",
        "name: ${LOCAL_ADAPTER_WORKER_LOGS_VOLUME:-opendispatch-local-adapter-worker-logs}",
        "name: ${LOCAL_ADMIN_UI_LOGS_VOLUME:-opendispatch-local-admin-ui-logs}",
    ])


    assert_contains(local_smoke, [
        "/actuator/health",
        "/actuator/health/liveness",
        "wait_core_health_up",
        "Last health body",
        "Core/Redis/PostgreSQL/Flyway logs",
        "check_redis_ping",
        "redis-cli ping",
        "/api/core/status",
        "agentRemediationWorkflowMetricsEnabled",
        "idempotencyEnabled",
        "wait_tcp",
        "SKIP_ADMIN_UI_SMOKE",
        "check_postgres_table",
        "module_outbox_events",
        "flyway_schema_history",
        "Recent Admin UI container log",
        "logs --tail=240 admin-ui",
        "api-runtime-smoke.mjs",
        "high-risk Core/Netty/Admin API runtime smoke acceptance",
        "Admin UI proxy E2E smoke",
        "ai-event-gateway-admin-ui/scripts/e2e-smoke.mjs",
        "Admin UI route-level smoke",
        "ai-event-gateway-admin-ui/scripts/route-smoke.mjs",
        "RUN_RUNTIME_LIFECYCLE_E2E",
        "P26 Core/Netty/Admin UI/Agent runtime lifecycle E2E",
        "runtime-lifecycle-e2e.sh",
    ])

    # Guardrail: P13 must not add production Jackson 2 imports while adding automation.
    jackson2_hits: list[str] = []
    for path in (ROOT / "ai-event-gateway-core").glob("*/src/main/java/**/*.java"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "com.fasterxml.jackson" in text:
            jackson2_hits.append(str(path.relative_to(ROOT)))
    if jackson2_hits:
        fail("Jackson 2 imports found in production code: " + ", ".join(jackson2_hits[:10]))

    print("P14 local CI/CD runtime verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
