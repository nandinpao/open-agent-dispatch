#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []

beginner = (ROOT / "ai-event-gateway-admin-ui/lib/dispatch-readiness/beginnerWorkflow.ts").read_text()
checks.append(("CANCELLED is treated as done headline", "['CANCELLED', 'CANCELED'].includes(status)" in beginner and "任務已取消" in beginner))
checks.append(("done lane wins before danger", "if (summary.tone === 'success' || ['COMPLETED', 'CANCELLED', 'CANCELED', 'SUCCEEDED', 'RESOLVED'].includes(status)) return 'done';" in beginner))
checks.append(("CANCELLED is not failed relationship", "['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(normalizeCode(task?.status))" in beginner))

workbench = (ROOT / "ai-event-gateway-admin-ui/lib/tasks/taskWorkbench.ts").read_text()
checks.append(("workbench success before stale dispatch failure", workbench.index("if (['COMPLETED', 'SUCCEEDED'].includes(status) || execution === 'COMPLETED')") < workbench.index("if (task.failureReason || ['FAILED', 'TIMEOUT', 'TIMED_OUT', 'DEAD_LETTER'].includes(status) || execution === 'FAILED')")))
checks.append(("workbench cancelled closed", "Task 已取消結案" in workbench))

list_view = (ROOT / "ai-event-gateway-admin-ui/components/tasks/TaskTable.tsx").read_text()
checks.append(("closed tasks suppress retry", "if (isClosedTaskStatus(taskStatus)) return false;" in list_view))
checks.append(("terminal tasks suppress dispatch error summary", "if ([\"COMPLETED\", \"SUCCEEDED\", \"RESOLVED\", \"CANCELLED\", \"CANCELED\"].includes(taskStatus)) return undefined;" in list_view))

core = (ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreDashboardController.java").read_text()
checks.append(("backend logs terminal historical dispatch", "task_runtime_view_terminal_historical_dispatch" in core))
checks.append(("backend uses latest dispatch requests", "latestDispatchByTask" in core and "List<DispatchRequest> dispatchRequests" in core))

startup = (ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java").read_text()
checks.append(("core startup marker P6_15", "phase=P6_15" in startup))

gateway = (ROOT / "ai-event-gateway-netty/gateway-app/src/main/java/com/opensocket/aievent/gateway/netty/GatewayDiagnosticsStartupLogger.java").read_text()
checks.append(("gateway startup marker P6_15", "phase=P6_15" in gateway))

failed = [name for name, ok in checks if not ok]
if failed:
    print("P6.15 verification failed:")
    for name in failed:
        print(f" - {name}")
    raise SystemExit(1)
print("P6.15 terminal task UI classification verification passed")
