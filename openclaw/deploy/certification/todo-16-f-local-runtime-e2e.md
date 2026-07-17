# TODO 16-F Local Runtime E2E Certification Runbook

This runbook validates the OpenClaw OpenSocket plugin after applying TODO 16-A through TODO 16-F.

## Prerequisites

- Node.js `>=22.19.0`
- npm dependencies installed with `npm ci`
- No live Core, Netty, or OpenClaw service is required for this deterministic local gate.

## Run

```bash
npm run verify:16f
```

The command runs:

1. `npm run version:check`
2. `npm run clean`
3. `npm run build`
4. `npm test`
5. `npm run runtime:16f:check`

## Certification scenarios

1. Offer-based execution path: `task.offer -> task.assign -> OpenClaw run -> task.completed`.
2. Fencing isolation path: stale callback rejection for the old assignment does not affect the new assignment.
3. Reconnect/outbox path: disconnected terminal result is resent after restart with stable callback and fencing metadata.

## Failure handling

- If the offer path fails, check `TaskOfferRouter`, `TaskReliabilityRuntime`, and `TaskStateReporter` correlation propagation.
- If fencing isolation fails, check `ResultOutbox` event identity and `TaskTerminalAckPayload` handling.
- If reconnect resend fails, check `TaskAssignmentRegistry.reconcileTerminalOutbox` and `ResultOutbox` in-flight recovery.
