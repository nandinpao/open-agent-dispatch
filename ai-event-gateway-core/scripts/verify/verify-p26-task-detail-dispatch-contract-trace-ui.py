#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADMIN = ROOT.parent / "ai-event-gateway-admin-ui"
checks = {
    "Task trace panel": ADMIN / "components/tasks/TaskDispatchContractTracePanel.tsx",
    "Task detail view": ADMIN / "components/tasks/TaskDetailView.tsx",
    "Task detail hook": ADMIN / "hooks/useTaskDetail.ts",
    "Core admin API": ADMIN / "lib/api/coreAdminApi.ts",
    "Core types": ADMIN / "lib/types/core.ts",
}
for name, path in checks.items():
    if not path.exists():
        raise SystemExit(f"missing {name}: {path}")

panel = checks["Task trace panel"].read_text()
for needle in [
    "TaskDispatchContractTracePanel",
    "CoreDispatchContractTraceResponse",
    "無法 Assign Agent 問題回報與修復建議",
    "可貼到 Issue 的問題回報",
    "readableAdvice",
    "Open Dispatch Capability",
    "Repair Dispatch Capability",
]:
    if needle not in panel:
        raise SystemExit(f"TaskDispatchContractTracePanel missing {needle}")

hook = checks["Task detail hook"].read_text()
for needle in [
    "CoreDispatchContractTraceResponse",
    "dispatchContractTrace?: CoreDispatchContractTraceResponse",
    "coreAdminApi.traceDispatchContract({ taskId })",
    "dispatchContractTraceError",
]:
    if needle not in hook:
        raise SystemExit(f"useTaskDetail missing {needle}")

detail = checks["Task detail view"].read_text()
for needle in [
    "TaskDispatchContractTracePanel",
    "dispatchContractTrace: data.dispatchContractTrace",
    "traceBlockingReason",
    "先修正 Dispatch Contract Trace",
    "input.dispatchContractTrace?.firstBlockingCode",
]:
    if needle not in detail:
        raise SystemExit(f"TaskDetailView missing {needle}")

api = checks["Core admin API"].read_text()
if "traceDispatchContract(body: CoreDispatchContractTraceRequest)" not in api:
    raise SystemExit("coreAdminApi missing traceDispatchContract client")

types = checks["Core types"].read_text()
for needle in [
    "export interface CoreDispatchContractTraceResponse",
    "taskTypeCode?: string",
    "effectiveTaskTypeCode?: string",
]:
    if needle not in types:
        raise SystemExit(f"core.ts missing {needle}")

print("P26 Task Detail dispatch contract trace UI static verification passed.")
