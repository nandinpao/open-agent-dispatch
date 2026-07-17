#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        errors.append(f"missing file: {path}")
        return ""
    return p.read_text(errors="ignore")

def require(path: str, token: str, label: str | None = None) -> None:
    text = read(path)
    if token not in text:
        errors.append(f"{path}: missing {label or token!r}")

def forbid(path: str, token: str, label: str | None = None) -> None:
    text = read(path)
    if token in text:
        errors.append(f"{path}: forbidden {label or token!r}")

# Database master table exists and its source_systems definition carries no dispatch defaults.
baseline_path = "ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql"
baseline = read(baseline_path)
require(baseline_path, "create table if not exists source_systems")
source_block = baseline.split("create table if not exists source_systems", 1)[1].split("create table if not exists", 1)[0] if "create table if not exists source_systems" in baseline else baseline
for forbidden in ["default_profile", "default_scope", "default_capability", "operation_profile", "agent_coverage", "source_default"]:
    if forbidden in source_block:
        errors.append(f"{baseline_path}: source_systems block must not contain {forbidden!r}")

# Backend pure CRUD API exists.
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/SourceSystemController.java", "@RequestMapping(\"/admin/source-systems\")")
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/source/SourceSystemManagementService.java", "from source_systems")
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/source/SourceSystemView.java", "private String sourceSystemId;")

# Source Systems UI uses pure source-system CRUD, not dispatch-contract derived options.
require("ai-event-gateway-admin-ui/components/source-systems/SourceSystemConsole.tsx", "coreAdminApi.getSourceSystems")
require("ai-event-gateway-admin-ui/components/source-systems/SourceSystemConsole.tsx", "coreAdminApi.createSourceSystem")
require("ai-event-gateway-admin-ui/components/source-systems/SourceSystemConsole.tsx", "coreAdminApi.updateSourceSystem")
for forbidden in ["getDispatchContractSourceSystems", "建立來源並建立派工流程", "defaultProfile", "defaultScope", "defaultCapability", "Operation Profile", "Agent Coverage"]:
    forbid("ai-event-gateway-admin-ui/components/source-systems/SourceSystemConsole.tsx", forbidden)

# Dispatch Flow builder selects existing Source Systems and no longer creates them implicitly.
require("ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx", "coreAdminApi.getSourceSystems")
require("ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx", "建立 Flow 不會自動建立來源")
for forbidden in ["getDispatchContractSourceSystems", "NEW_SOURCE_VALUE", "isNewSource", "＋ 新增來源系統", "新來源識別碼"]:
    forbid("ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx", forbidden)

# API client exposes source-system CRUD.
require("ai-event-gateway-admin-ui/lib/api/endpoints.ts", "sourceSystems: '/admin/source-systems'")
require("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts", "getSourceSystems")
require("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts", "createSourceSystem")
require("ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts", "updateSourceSystem")

# Compatibility source options must be sourced from source_systems master data now.
require("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchContractController.java", "source_systems master table")
for forbidden in ["assignmentService.sourceSystemOptions"]:
    forbid("ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchContractController.java", forbidden)

if errors:
    print("Stage 6 Source System master-data contract failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)
print("Stage 6 Source System master-data contract verified.")
