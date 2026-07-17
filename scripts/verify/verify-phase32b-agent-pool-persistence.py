#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(rel: str, token: str, label: str) -> None:
    text = read(rel)
    if token not in text:
        errors.append(f"{rel} missing {label}: {token!r}")


def forbid(rel: str, token: str, label: str) -> None:
    text = read(rel)
    if token in text:
        errors.append(f"{rel} contains forbidden {label}: {token!r}")

sql = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
phase = "docs/PHASE32_B_AGENT_POOL_PERSISTENCE_CONTRACT.md"
current = "docs/CURRENT_DISPATCH_DOMAIN_MODEL.md"
adr = "docs/ADR-Dispatch-Authority.md"
service = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java"
flow_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowView.java"
rule_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRuleView.java"
pool_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/AgentPoolView.java"
pool_member_view = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/AgentPoolMemberView.java"
ts_types = "ai-event-gateway-admin-ui/lib/types/core.ts"
makefile = "Makefile"
package = "ai-event-gateway-admin-ui/package.json"

for rel in (sql, phase, current, adr, service, flow_view, rule_view, pool_view, pool_member_view, ts_types, makefile, package):
    read(rel)

for token in (
    "create table if not exists agent_pools",
    "pool_type varchar(32) not null default 'RESOLUTION'",
    "selection_strategy varchar(32) not null default 'LOWEST_LOAD'",
    "primary key (tenant_id, pool_id)",
    "unique (tenant_id, pool_code)",
    "create table if not exists agent_pool_members",
    "primary key (tenant_id, pool_id, agent_id)",
    "idx_agent_pool_members_agent",
    "flow_type varchar(32) not null default 'SOURCE_FLOW'",
    "default_pool_id varchar(128)",
    "idx_dispatch_flows_default_pool",
    "priority int not null default 100",
    "match_mode varchar(32) not null default 'EXACT_OR_WILDCARD'",
    "target_pool_id varchar(128)",
    "target_pool_code varchar(128)",
    "create or replace view dispatch_flow_rules as",
    "policy_id as rule_id",
    "target_pool_id",
    "classification_status varchar(32) not null default 'CLASSIFIED'",
    "classification_result_json jsonb not null default '{}'::jsonb",
    "task_assignments",
    "assigned_pool_id varchar(128)",
):
    require(sql, token, f"Phase 32-B SQL token {token}")

for token in (
    "Capability rows are retained as Agent metadata / compatibility records only",
    "does not allow Capability to become a first-version routing gate",
    "Agent Pool / Work Queue persistence",
):
    require(sql, token, f"SQL capability boundary token {token}")

for token in (
    "Status: **Completed in this patch as DB/data-contract groundwork**",
    "agent_pools",
    "agent_pool_members",
    "dispatch_flows",
    "default_pool_id",
    "dispatch_policies",
    "target_pool_id",
    "dispatch_flow_rules",
    "Capability is still Agent metadata only",
    "No production routing engine refactor yet.",
    "Phase 32-C should relax intake normalization",
):
    require(phase, token, f"Phase 32-B doc token {token}")

for token in (
    "Phase 32-B persistence state",
    "dispatch_flow_rules compatibility view",
    "Capability remains Agent metadata / compatibility data only",
):
    require(current, token, f"current model Phase 32-B token {token}")

for token in (
    "Phase 32-B persistence amendment",
    "Source Flow default_pool_id",
    "Flow Rule target_pool_id / target_pool_code",
    "Agent Pool Member",
):
    require(adr, token, f"ADR Phase 32-B token {token}")

for token in (
    "private String flowType = \"SOURCE_FLOW\";",
    "private String defaultPoolId;",
    "getDefaultPoolId()",
):
    require(flow_view, token, f"DispatchFlowView token {token}")

for token in (
    "private String matchMode = \"EXACT_OR_WILDCARD\";",
    "private String targetPoolId;",
    "private String targetPoolCode;",
    "getTargetPoolId()",
):
    require(rule_view, token, f"DispatchFlowRuleView token {token}")

for token in (
    "class AgentPoolView",
    "private String poolType = \"RESOLUTION\";",
    "private String selectionStrategy = \"LOWEST_LOAD\";",
):
    require(pool_view, token, f"AgentPoolView token {token}")

for token in (
    "class AgentPoolMemberView",
    "private Integer priority = 100;",
    "private Integer weight = 1;",
):
    require(pool_member_view, token, f"AgentPoolMemberView token {token}")

for token in (
    "CoreAgentPoolView",
    "CoreAgentPoolMemberView",
    "flowType?: 'SOURCE_FLOW' | string;",
    "defaultPoolId?: string;",
    "matchMode?: string;",
    "targetPoolId?: string;",
    "targetPoolCode?: string;",
):
    require(ts_types, token, f"Admin UI type token {token}")

for token in (
    ".addValue(\"flowType\", firstNonBlank(normalized.getFlowType(), \"SOURCE_FLOW\"))",
    ".addValue(\"defaultPoolId\", preserveNullableId(normalized.getDefaultPoolId()))",
    "flow_type, default_pool_id",
    ".addValue(\"matchMode\", firstNonBlank(rule.getMatchMode(), \"EXACT_OR_WILDCARD\"))",
    ".addValue(\"targetPoolId\", preserveNullableId(rule.getTargetPoolId()))",
    ".addValue(\"targetPoolCode\", normalizeNullable(rule.getTargetPoolCode()))",
    "priority, match_mode, target_pool_id, target_pool_code",
    "phase32bTargetPoolPersistence",
    "private static String preserveNullableId",
):
    require(service, token, f"DispatchFlowManagementService persistence token {token}")

for token in (
    "verify-phase32b-agent-pool-persistence",
    "phase32-b",
):
    require(makefile, token, f"Makefile Phase 32-B token {token}")

require(package, "verify:phase32b-agent-pool-persistence", "Admin UI package Phase 32-B script")

# Guard against reintroducing the rejected model in Phase 32-B materials.
for rel in (phase, current, adr):
    for token in (
        "SourceSystem -> Flow Rule -> Capability -> Agent",
        "required Capability routing gate in Phase 32-B",
        "Capability becomes the dispatch target",
    ):
        forbid(rel, token, f"rejected Phase 32-B capability-routing wording {token}")

if errors:
    print("Phase 32-B Agent Pool persistence verification failed:", file=sys.stderr)
    for err in errors:
        print(f" - {err}", file=sys.stderr)
    sys.exit(1)

print("Phase 32-B Agent Pool persistence contract verified.")
