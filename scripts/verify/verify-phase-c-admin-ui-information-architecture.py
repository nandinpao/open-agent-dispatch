#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

required_files = [
    "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts",
    "ai-event-gateway-admin-ui/components/common/InformationArchitectureGuide.tsx",
    "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx",
    "ai-event-gateway-admin-ui/components/dashboard/DualPlaneDashboard.tsx",
    "docs/PHASE_C_ADMIN_UI_INFORMATION_ARCHITECTURE.md",
]

for rel in required_files:
    if not (ROOT / rel).is_file():
        print(f"[FAIL] Missing required Phase C file: {rel}")
        raise SystemExit(1)

checks = {
    "ai-event-gateway-admin-ui/lib/navigation/adminInformationArchitecture.ts": [
        "Business Truth",
        "Dispatch Truth",
        "Runtime Diagnostics",
        "CORE_AUTHORITY",
        "DISPATCH_LEDGER",
        "RUNTIME_DIAGNOSTICS",
        "Core PostgreSQL / Core Admin API",
        "Core Dispatch Request / Timeline / Callback Inbox / Attempt Ledger",
        "Netty Admin API / WebSocket Runtime Stream",
    ],
    "ai-event-gateway-admin-ui/components/common/InformationArchitectureGuide.tsx": [
        "Admin UI Information Architecture",
        "先判斷資料層，再判斷操作",
        "Core = 權威狀態；Netty = transport 診斷；兩者不可互相覆蓋。",
        "查看三層總覽",
    ],
    "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx": [
        "adminInformationLayers",
        "Business · Dispatch · Runtime",
        "三層判讀原則",
    ],
    "ai-event-gateway-admin-ui/components/dashboard/DualPlaneDashboard.tsx": [
        "Three-layer Operations Dashboard",
        "Business Truth + Dispatch Truth + Runtime Diagnostics",
        "Business Truth / Core Authority",
        "Dispatch Truth / Callback Ledger",
        "Runtime Diagnostics",
        "delivery event 只能證明 transport 嘗試",
    ],
    "docs/PHASE_C_ADMIN_UI_INFORMATION_ARCHITECTURE.md": [
        "Layer 1 - Business Truth",
        "Layer 2 - Dispatch Truth",
        "Layer 3 - Runtime Diagnostics",
        "Operator reading order",
    ],
}

for rel, needles in checks.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            print(f"[FAIL] {rel} does not contain expected marker: {needle}")
            raise SystemExit(1)

pages_with_guides = {
    "ai-event-gateway-admin-ui/app/dashboard/page.tsx": "<InformationArchitectureGuide />",
    "ai-event-gateway-admin-ui/app/tasks/page.tsx": "activeLayer=\"business\"",
    "ai-event-gateway-admin-ui/app/agents/page.tsx": "activeLayer=\"business\"",
    "ai-event-gateway-admin-ui/app/settings/capabilities/page.tsx": "activeLayer=\"dispatch\"",
    "ai-event-gateway-admin-ui/app/testing/dispatch-recipes/page.tsx": "activeLayer=\"dispatch\"",
    "ai-event-gateway-admin-ui/app/testing/dispatch-readiness/page.tsx": "activeLayer=\"dispatch\"",
    "ai-event-gateway-admin-ui/app/tasks/failure-queue/page.tsx": "activeLayer=\"dispatch\"",
    "ai-event-gateway-admin-ui/app/cluster/page.tsx": "activeLayer=\"runtime\"",
    "ai-event-gateway-admin-ui/app/cluster/diagnostics/page.tsx": "activeLayer=\"runtime\"",
    "ai-event-gateway-admin-ui/app/websocket/page.tsx": "activeLayer=\"runtime\"",
    "ai-event-gateway-admin-ui/app/runtime/rejected-connections/page.tsx": "activeLayer=\"runtime\"",
}

for rel, marker in pages_with_guides.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    if "InformationArchitectureGuide" not in text or marker not in text:
        print(f"[FAIL] {rel} is missing Phase C guide marker: {marker}")
        raise SystemExit(1)


legacy_skills = (ROOT / "ai-event-gateway-admin-ui/app/skills/page.tsx").read_text(encoding="utf-8")
if "redirect('/settings/capabilities?legacy=skills')" not in legacy_skills:
    print("[FAIL] Legacy Skills route must redirect to the governed capability diagnostics page.")
    raise SystemExit(1)

sidebar_text = (ROOT / "ai-event-gateway-admin-ui/components/layout/Sidebar.tsx").read_text(encoding="utf-8")
if "const navItems" in sidebar_text:
    print("[FAIL] Sidebar must not regress to a flat navItems list; use adminInformationLayers.")
    raise SystemExit(1)

verify_release = (ROOT / "scripts/verify/verify-release.py").read_text(encoding="utf-8")
if "verify-phase-c-admin-ui-information-architecture.py" not in verify_release:
    print("[FAIL] verify-release.py must include the Phase C information architecture gate.")
    raise SystemExit(1)

print("[PASS] Phase C Admin UI information architecture markers verified")
