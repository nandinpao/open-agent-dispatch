#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
TEST_ROOT = ROOT / "ai-event-gateway-core/agent-control/src/test/java"

def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        raise AssertionError(f"missing required file: {path}")
    return p.read_text(encoding="utf-8")

checks = []

runtime_test = read("ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/assignment/AgentRuntimeBindingIdempotencyTest.java")
checks.append(("runtime binding idempotency test no longer imports deleted in-memory repository", "InMemoryAgentAssignmentRepository" not in runtime_test))
checks.append(("runtime binding idempotency test uses current repository contract", "AgentAssignmentRepository repository = mock(AgentAssignmentRepository.class);" in runtime_test))
checks.append(("runtime binding idempotency test stubs tenant+agent active binding lookup", "findActiveRuntimeBindingByTenantAndAgent" in runtime_test))
checks.append(("runtime binding idempotency test still proves active binding reuse", "assertThat(second.getBindingId()).isEqualTo(bindingId);" in runtime_test))

setup_test = read("ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/setup/AgentSetupServiceTest.java")
forbidden_setup_tokens = [
    "upsertSupplyProfile(",
    "findSupplyProfilesByAgent(",
    "getTaskScope(",
    "new SupplyProfile(",
    "import com.opensocket.aievent.core.agent.assignment.SupplyProfile;",
]
for token in forbidden_setup_tokens:
    checks.append((f"AgentSetupServiceTest removed stale {token}", token not in setup_test))
checks.append(("AgentSetupServiceTest expects optional capability readiness", "OPTIONAL_CAPABILITIES_READY" in setup_test))
checks.append(("AgentSetupServiceTest forbids legacy readiness checks", "doesNotContain(\"CAPABILITIES_ASSIGNED\", \"SERVICE_SCOPE_ACTIVE\", \"DISPATCH_RULE_ACTIVE\")" in setup_test))

all_tests = "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in TEST_ROOT.rglob("*.java"))
for token in ["InMemoryAgentAssignmentRepository", "upsertSupplyProfile(", "findSupplyProfilesByAgent(", "getTaskScope("]:
    checks.append((f"agent-control tests contain no stale compile token {token}", token not in all_tests))

failed = [name for name, ok in checks if not ok]
if failed:
    print("Stage 15 agent-control testCompile repair verification failed:")
    for name in failed:
        print(f" - {name}")
    sys.exit(1)

print("Stage 15 agent-control testCompile repair contract verified.")
