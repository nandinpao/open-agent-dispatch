#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    sys.exit(1)


def require_file(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"Missing required file: {path}")
    return p.read_text()


def require_contains(path: str, *needles: str) -> None:
    text = require_file(path)
    for needle in needles:
        if needle not in text:
            fail(f"{path} must contain {needle!r}")


def require_not_contains(path: str, *needles: str) -> None:
    text = require_file(path)
    for needle in needles:
        if needle in text:
            fail(f"{path} must not contain {needle!r}")


def main() -> int:
    require_contains(
        'ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/AdapterSecretRedactor.java',
        'public final class AdapterSecretRedactor',
        'redactMap',
        'redactText',
        'safeHttpTimeout',
        'authorization',
        '[REDACTED]',
    )
    require_contains(
        'ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/AbstractHttpIssueVendorExecutor.java',
        'AdapterSecretRedactor.redactText(body)',
        'AdapterSecretRedactor.redactText(error)',
        'AdapterSecretRedactor.safeHttpTimeout',
        'X-OpenDispatch-Idempotency-Key',
    )
    require_contains(
        'ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/audit/AdapterExecutorAuditService.java',
        'AdapterSecretRedactor.redactText',
        'AdapterSecretRedactor.redactMap',
    )
    require_contains(
        'ai-event-gateway-core/adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/mcp/HttpMcpActionExecutor.java',
        'AdapterSecretRedactor.redactText(response.body())',
        'AdapterSecretRedactor.redactThrowableMessage',
        'AdapterSecretRedactor.safeHttpTimeout',
    )
    require_contains(
        'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidator.java',
        'validateProductionIssueExecutorReadiness',
        'REDMINE_EXECUTOR_API_KEY',
        'GITLAB_EXECUTOR_PRIVATE_TOKEN',
        'GITLAB_EXECUTOR_PROJECT_ID',
        'ISSUE_EXECUTOR_DEFAULT_VENDOR=JIRA',
        'adapter-executor.execution-timeout',
    )
    require_contains(
        'ai-event-gateway-core/adapter-action/src/test/java/com/opensocket/aievent/core/action/executor/AdapterSecretRedactorTest.java',
        'shouldRedactSensitivePayloadKeysRecursively',
        'shouldRedactSecretsFromTextAndUrls',
        'shouldClampUnsafeHttpTimeouts',
    )
    require_contains(
        'ai-event-gateway-core/adapter-action/src/test/java/com/opensocket/aievent/core/action/executor/audit/AdapterExecutorAuditServiceRedactionTest.java',
        'shouldRedactPayloadSnapshotAndMessageBeforePersistingAuditRecord',
    )
    require_contains(
        'ai-event-gateway-core/adapter-action/src/test/java/com/opensocket/aievent/core/action/executor/issue/IssueVendorExecutorContractTest.java',
        'vendorHttpFailureShouldRedactSecretsFromOperatorFacingError',
        'doesNotContain("gitlab-secret-token", "bearer-secret")',
    )
    require_contains(
        'ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidatorTest.java',
        'shouldRejectRedmineDefaultVendorWithoutRealExecutorInProdProfile',
        'shouldRejectPlaceholderRedmineApiKeyInProdProfile',
        'shouldRejectLocalGitlabEndpointInProdProfile',
        'shouldRejectPreEncodedGitlabProjectIdInProdProfile',
        'shouldRejectJiraDefaultVendorUntilRealJiraExecutorProfileExistsInProdProfile',
        'shouldAllowRealRedmineIssueExecutorInProdProfile',
    )
    require_contains(
        'docs/P3_PRODUCTION_SECURITY_READINESS.md',
        'P3',
        'secret redaction',
        'production readiness',
    )
    require_contains('scripts/verify/verify-release.py', 'P3 production security readiness', 'verify-p3-production-security-readiness.py')
    print('P3 production security readiness verification passed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
