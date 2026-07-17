#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors = []

def require(path, tokens):
    p = ROOT / path
    if not p.exists():
        errors.append(f"missing {path}")
        return
    text = p.read_text()
    for token in tokens:
        if token not in text:
            errors.append(f"{path} missing {token!r}")

require('ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage8AgentSetupFlowPreconditionContainerTest.java', [
    'setupAgentIsIdempotentAndThenDispatchFlowCanSelectTheAgent',
    'setupService.setupAgent(setupRequest())',
    'flowService().createOrUpdateFlow(flowAggregate())',
    'agent_runtime_bindings',
    'flow_agent_assignments',
])
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ApiExceptionHandler.java', [
    'ResponseEntity.status(statusFor',
    'handleDataIntegrity',
    'AGENT_RUNTIME_BINDING_CONFLICT',
    'INTERNAL_ERROR.defaultMessage()',
])
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/StandardApiResponseAdvice.java', [
    'Preserve the status selected by ResponseEntity/exception handlers',
])
require('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/api/StandardApiErrorCode.java', [
    'TENANT_CONTEXT_REQUIRED',
    'FLOW_AGENT_PROFILE_NOT_FOUND',
    'AGENT_RUNTIME_BINDING_CONFLICT',
])
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java', [
    'dispatchFlowMutationException',
    'FLOW_AGENT_PROFILE_NOT_FOUND',
    'StandardApiException(StandardApiErrorCode.NOT_FOUND',
])

if errors:
    print('Stage8-F0f flow/agent setup error contract verification failed:', file=sys.stderr)
    for e in errors:
        print(f' - {e}', file=sys.stderr)
    sys.exit(1)
print('Stage8-F0f flow/agent setup error contract static verification passed.')
