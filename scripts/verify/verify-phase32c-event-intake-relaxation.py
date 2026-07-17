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


def forbid_between(rel: str, start: str, end: str, token: str, label: str) -> None:
    text = read(rel)
    if start not in text or end not in text:
        errors.append(f"{rel} cannot inspect {label}; missing boundary")
        return
    segment = text.split(start, 1)[1].split(end, 1)[0]
    if token in segment:
        errors.append(f"{rel} contains forbidden {label}: {token!r}")

request = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java"
normalizer = "ai-event-gateway-core/event-processing/src/main/java/com/opensocket/aievent/core/normalize/EventNormalizer.java"
task_type = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskType.java"
task_record = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/TaskRecord.java"
task_po = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/task/po/TaskPo.java"
converter = "ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/task/converter/TaskPersistenceConverter.java"
task_xml = "ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml"
task_service = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskDecisionService.java"
ts_types = "ai-event-gateway-admin-ui/lib/types/core.ts"
ts_api = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
phase_doc = "docs/PHASE32_C_EVENT_INTAKE_RELAXATION_CONTRACT.md"
current = "docs/CURRENT_DISPATCH_DOMAIN_MODEL.md"
adr = "docs/ADR-Dispatch-Authority.md"
test = "ai-event-gateway-core/event-processing/src/test/java/com/opensocket/aievent/core/normalize/EventNormalizerOptionalFieldsTest.java"
makefile = "Makefile"
package = "ai-event-gateway-admin-ui/package.json"

for rel in (request, normalizer, task_type, task_record, task_po, converter, task_xml, task_service, ts_types, ts_api, phase_doc, current, adr, test, makefile, package):
    read(rel)

for token in (
    "@NotBlank\n    private String tenantId;",
    "@NotBlank\n    private String sourceSystem;",
    "Optional from Phase 32-C onward",
    "private String eventType;",
):
    require(request, token, f"intake request token {token}")
forbid_between(request, "Optional from Phase 32-C onward", "private String errorCode;", "@NotBlank", "eventType must not be @NotBlank")

for token in (
    "public static final String UNKNOWN = \"UNKNOWN\";",
    "cleanUpper(defaultString(request.getObjectType(), UNKNOWN))",
    "cleanUpper(defaultString(request.getEventType(), UNKNOWN))",
    "cleanUpper(defaultString(request.getErrorCode(), UNKNOWN))",
    "classificationStatus",
):
    require(normalizer, token, f"normalizer token {token}")

require(task_type, "TRIAGE", "TRIAGE task type")

for token in (
    "private String assignedPoolId;",
    "private String targetPoolId;",
    "private String classificationStatus = \"CLASSIFIED\";",
    "private String classificationResultJson = \"{}\";",
    "getClassificationStatus()",
):
    require(task_record, token, f"TaskRecord pool/classification token {token}")

for token in (
    "private String assignedPoolId;",
    "private String targetPoolId;",
    "private String classificationStatus;",
    "private String classificationResultJson;",
):
    require(task_po, token, f"TaskPo pool/classification token {token}")

for token in (
    "po.setAssignedPoolId(task.getAssignedPoolId())",
    "po.setTargetPoolId(task.getTargetPoolId())",
    "po.setClassificationStatus(firstNonBlank(task.getClassificationStatus(), \"CLASSIFIED\"))",
    "po.setClassificationResultJson(firstNonBlank(task.getClassificationResultJson(), \"{}\"))",
    "task.setClassificationStatus(firstNonBlank(po.getClassificationStatus(), \"CLASSIFIED\"))",
    "task.setClassificationResultJson(firstNonBlank(po.getClassificationResultJson(), \"{}\"))",
):
    require(converter, token, f"converter token {token}")

for token in (
    "assigned_pool_id",
    "target_pool_id",
    "classification_status",
    "classification_result_json",
    "cast(#{task.classificationResultJson} as jsonb)",
):
    require(task_xml, token, f"TaskDao pool/classification token {token}")

for token in (
    "boolean unclassifiedEvent = isUnclassifiedEvent(event);",
    "TaskType responseTaskType = unclassifiedEvent ? TaskType.TRIAGE : TaskType.INCIDENT_RESPONSE;",
    "missing classification normalized to UNKNOWN; TRIAGE task created",
    "task.setTaskTypeCode(firstNonBlank(resolution.taskTypeCode(), taskType == TaskType.TRIAGE ? \"TRIAGE\" : null))",
    "task.setClassificationStatus(classificationStatusFor(event));",
    "task.setClassificationResultJson(\"{}\");",
    "SOURCE_FLOW_TRIAGE_PENDING",
    "private boolean isUnclassifiedEvent(NormalizedEvent event)",
    "private String classificationStatusFor(NormalizedEvent event)",
):
    require(task_service, token, f"TaskDecisionService Phase 32-C token {token}")

for token in (
    "assignedPoolId?: string;",
    "targetPoolId?: string;",
    "classificationStatus?: 'UNCLASSIFIED'",
    "classificationResultJson?: unknown;",
):
    require(ts_types, token, f"Admin UI types token {token}")

for token in (
    "pickString(taskRecord, [\"assignedPoolId\", \"assigned_pool_id\"])",
    "pickString(taskRecord, [\"targetPoolId\", \"target_pool_id\"])",
    "pickString(taskRecord, [\"classificationStatus\", \"classification_status\"])",
    "classificationResultJson",
):
    require(ts_api, token, f"Admin UI API mapper token {token}")

for token in (
    "Status: **Completed in this patch as API/domain normalization groundwork**",
    "tenantId required",
    "sourceSystem required",
    "eventType optional",
    "eventType = UNKNOWN",
    "classificationStatus = UNCLASSIFIED",
    "taskType = TRIAGE",
    "Phase 32-D should move runtime routing",
):
    require(phase_doc, token, f"Phase 32-C doc token {token}")

for token in (
    "Phase 32-C intake relaxation state",
    "tenantId + sourceSystem",
    "taskType = TRIAGE",
):
    require(current, token, f"current model Phase 32-C token {token}")

for token in (
    "Phase 32-C intake relaxation amendment",
    "source-system-only payloads",
    "classification_status=UNCLASSIFIED",
):
    require(adr, token, f"ADR Phase 32-C token {token}")

for token in (
    "shouldAcceptSourceSystemOnlyIntakeAndNormalizeUnknownClassification",
    "assertThat(event.eventType()).isEqualTo(\"UNKNOWN\")",
    "assertThat(event.errorCode()).isEqualTo(\"UNKNOWN\")",
):
    require(test, token, f"normalizer test token {token}")

require(makefile, "verify-phase32c-event-intake-relaxation", "Makefile Phase 32-C verify target")
require(makefile, "phase32-c", "Makefile Phase 32-C aggregate target")
require(package, "verify:phase32c-event-intake-relaxation", "Admin UI package Phase 32-C script")

if errors:
    print("Phase 32-C event intake relaxation verification failed:", file=sys.stderr)
    for err in errors:
        print(f" - {err}", file=sys.stderr)
    sys.exit(1)

print("Phase 32-C event intake relaxation contract verified.")
