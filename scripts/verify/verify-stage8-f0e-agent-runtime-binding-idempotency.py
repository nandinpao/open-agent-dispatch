#!/usr/bin/env python3
from pathlib import Path
import argparse
import zipfile

ROOT = Path(__file__).resolve().parents[2]
XML_PATH = ROOT / "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.assignment/AgentAssignmentDao.xml"

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")

parser = argparse.ArgumentParser()
parser.add_argument("--require-artifact", action="store_true", help="also require built jar artifacts and verify embedded mapper SQL")
args = parser.parse_args()

checks = [
    ("repository tenant-agent finder", ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentRepository.java", "findActiveRuntimeBindingByTenantAndAgent(String tenantId, String agentId)"),
    ("repository tenant-agent update", ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentRepository.java", "updateActiveRuntimeBindingByTenantAndAgent(AgentRuntimeBinding binding)"),
    ("service reuses active tenant-agent binding", ROOT / "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java", "repository.findActiveRuntimeBindingByTenantAndAgent(tenantId, agentId)"),
    ("mybatis dao update method", ROOT / "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/assignment/dao/AgentAssignmentDao.java", "updateActiveRuntimeBindingByTenantAndAgent(@Param(\"binding\") AgentRuntimeBindingPo binding)"),
    ("mybatis repository catches active unique race", ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/assignment/repository/MybatisAgentAssignmentRepository.java", "catch (DuplicateKeyException conflict)"),
    ("mybatis repository update-first", ROOT / "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/agent/assignment/repository/MybatisAgentAssignmentRepository.java", "dao.updateActiveRuntimeBindingByTenantAndAgent(po)"),
    ("in-memory repository tenant-agent update", ROOT / "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/InMemoryAgentAssignmentRepository.java", "updateActiveRuntimeBindingByTenantAndAgent(AgentRuntimeBinding binding)"),
    ("idempotency unit test", ROOT / "ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/assignment/AgentRuntimeBindingIdempotencyTest.java", "upsertRuntimeBindingShouldReuseExistingActiveTenantAgentBinding"),
    ("container setup/flow test", ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/container/Stage8AgentSetupFlowPreconditionContainerTest.java", "setupAgentIsIdempotentAndThenDispatchFlowCanSelectTheAgent"),
]
errors = []
for label, path, token in checks:
    if not path.exists():
        errors.append(f"{label}: missing file {path.relative_to(ROOT)}")
        continue
    content = read(path)
    if token not in content:
        errors.append(f"{label}: missing token {token!r} in {path.relative_to(ROOT)}")

if not XML_PATH.exists():
    errors.append(f"missing mapper xml {XML_PATH.relative_to(ROOT)}")
else:
    xml = read(XML_PATH)
    required_tokens = [
        '<update id="updateActiveRuntimeBindingByTenantAndAgent">',
        'where tenant_id = #{binding.tenantId}',
        'and agent_id = #{binding.agentId}',
        "and binding_status = 'ACTIVE'",
        '<insert id="upsertRuntimeBinding">',
        'on conflict (binding_id) do update set',
    ]
    for token in required_tokens:
        if token not in xml:
            errors.append(f"mapper xml missing required token {token!r}")
    if xml.find('<update id="updateActiveRuntimeBindingByTenantAndAgent">') > xml.find('<insert id="upsertRuntimeBinding">'):
        errors.append("mapper xml must declare updateActiveRuntimeBindingByTenantAndAgent before upsertRuntimeBinding to make the update-first contract obvious")

# Built artifact verifier: when jars exist, any jar embedding AgentAssignmentDao.xml must contain the new update SQL.
jar_paths = list((ROOT / "ai-event-gateway-core").glob("**/target/*.jar"))
checked_mapper_artifacts = []
for jar in jar_paths:
    try:
        with zipfile.ZipFile(jar) as zf:
            names = zf.namelist()
            mapper_names = [name for name in names if name.endswith("mybatis/postgresql/agent.assignment/AgentAssignmentDao.xml")]
            # Spring Boot app jars may nest the database-platform jar. Verify nested jars too.
            nested_jars = [name for name in names if name.endswith(".jar") and ("database-platform" in name or "data-model" in name)]
            for mapper in mapper_names:
                content = zf.read(mapper).decode("utf-8", errors="replace")
                checked_mapper_artifacts.append(f"{jar.relative_to(ROOT)}!/{mapper}")
                if '<update id="updateActiveRuntimeBindingByTenantAndAgent">' not in content:
                    errors.append(f"built artifact mapper is stale: {jar.relative_to(ROOT)}!/{mapper}")
            for nested in nested_jars:
                data = zf.read(nested)
                import io
                with zipfile.ZipFile(io.BytesIO(data)) as nested_zf:
                    for mapper in [n for n in nested_zf.namelist() if n.endswith("mybatis/postgresql/agent.assignment/AgentAssignmentDao.xml")]:
                        content = nested_zf.read(mapper).decode("utf-8", errors="replace")
                        checked_mapper_artifacts.append(f"{jar.relative_to(ROOT)}!/{nested}!/{mapper}")
                        if '<update id="updateActiveRuntimeBindingByTenantAndAgent">' not in content:
                            errors.append(f"built nested mapper is stale: {jar.relative_to(ROOT)}!/{nested}!/{mapper}")
    except zipfile.BadZipFile:
        continue

if args.require_artifact and not checked_mapper_artifacts:
    errors.append("--require-artifact requested but no built jar containing AgentAssignmentDao.xml was found under ai-event-gateway-core/**/target/*.jar")

if errors:
    print("Stage8-F0e runtime binding SQL idempotency verification failed:")
    for error in errors:
        print(f" - {error}")
    raise SystemExit(1)

print("Stage8-F0e runtime binding SQL idempotency contract verified.")
if checked_mapper_artifacts:
    print("Verified built mapper artifacts:")
    for artifact in checked_mapper_artifacts:
        print(f" - {artifact}")
else:
    print("No built mapper artifact found; source-level mapper contract verified. Run with --require-artifact after mvn package to verify jars.")
