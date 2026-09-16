# P1.4 Agent Enrollment Rejected Approve Again Fix

## Problem

In P1.3, Agent Governance introduced reversible profile-level lifecycle support, but rejected enrollment-level rows could still fail to expose the `Approve Again` action.

The root cause was that some UI branches still compared the raw enrollment `status` string directly, for example:

```ts
status === 'REJECTED'
```

That is fragile because the rendered governance state may appear as `Enrollment rejected` while the raw value may arrive as lower-case, prefixed, or normalized by another layer, such as:

```text
rejected
ENROLLMENT_REJECTED
REJECTED
```

As a result, the table could display `Enrollment: Enrollment rejected`, but Actions would not consistently show `Approve Again`.

## Fix

### Admin UI

Added centralized enrollment status normalization in:

```text
lib/agents/governanceStatus.ts
```

New helpers:

```ts
normalizeEnrollmentStatus(status)
isOpenEnrollmentStatus(status)
isCorrectableEnrollmentStatus(status)
isRejectedEnrollmentStatus(status)
```

The helpers normalize all of these to the same actionable state:

```text
REJECTED
rejected
ENROLLMENT_REJECTED
```

### Agent Governance Actions

Updated:

```text
components/agents/AgentGovernanceWorkflowActions.tsx
components/agents/AgentEnrollmentReviewDialog.tsx
```

Rejected enrollments now consistently show:

```text
Approve Again
```

and route to the existing enrollment approval API:

```http
POST /admin/agent-enrollments/{enrollmentId}/approve
```

### Agent Enrollment Table

Updated:

```text
components/agents/AgentEnrollmentTable.tsx
```

Rejected enrollments are now treated as correctable review items:

```text
Enrollment rejected -> Edit Draft -> Credential Token -> Approve Again
```

The direct `Approve Again` button opens the draft editor if credential token is missing, instead of silently failing or appearing unavailable.

For rejected enrollments, `Reject` is hidden because the row is already rejected; the valid corrective action is `Approve Again`.

### Row selection / merge logic

Updated:

```text
hooks/useAgentGovernanceList.ts
```

Enrollment ranking now uses normalized statuses, so rejected enrollment records are consistently attached to the correct Agent row even if the status representation differs.

## Backend impact

No backend API change was required.

P1.3 Core already supports re-approving rejected enrollments through:

```http
POST /admin/agent-enrollments/{enrollmentId}/approve
```

and `AgentGovernanceService.approveEnrollment(...)` already writes:

```text
ENROLLMENT_REAPPROVED
```

when the previous enrollment status is `REJECTED`.

## Validation

Updated tests:

```text
tests/governance-status.test.ts
```

Added coverage for:

```text
rejected -> REJECTED
ENROLLMENT_REJECTED -> REJECTED
isRejectedEnrollmentStatus(...) = true
isCorrectableEnrollmentStatus(...) = true
```

This environment does not contain `node_modules`, so the frontend test command could not be executed here:

```text
node_modules missing
```

Please validate in your local/CI environment:

```bash
npm ci
npm run test:normalizers
npm run typecheck
npm run lint
```

Backend validation, if desired:

```bash
mvn -pl ai-event-gateway-core-agent-control,ai-event-gateway-core-app,ai-event-gateway-database-platform -am test
```
