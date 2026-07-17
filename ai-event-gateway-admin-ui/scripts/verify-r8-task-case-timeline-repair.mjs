import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
function content(rel) {
  return fs.readFileSync(path.join(root, rel), 'utf8');
}
function assertIncludes(rel, tokens) {
  const source = content(rel);
  for (const token of tokens) {
    if (!source.includes(token)) throw new Error(`${rel} missing token: ${token}`);
  }
}
function assertExcludes(rel, tokens) {
  const source = content(rel);
  for (const token of tokens) {
    if (source.includes(token)) throw new Error(`${rel} contains retired standard-workflow token: ${token}`);
  }
}

assertIncludes('../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V101__r8_task_case_timeline_fix_entry.sql', [
  'dispatch_r8_task_case_timeline_report', 'failure_stage', 'fix_action', 'matched_flow_id', 'matched_rule_id'
]);
assertIncludes('../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/task/timeline/TaskCaseTimelineView.java', [
  'matchedFlowId', 'matchedRuleId', 'failureStage', 'fixAction'
]);
assertIncludes('../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java', [
  '/tasks/{taskId}/case-timeline', 'TaskCaseTimelineView', 'NO_ACTIVE_FLOW_RULE', 'REQUIRED_CAPABILITY_MISSING', 'NO_ELIGIBLE_AGENT',
  'Capability is optional', 'Netty Delivery', 'Agent ACK', 'RESULT callback'
]);
assertExcludes('../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreAdminTaskFacadeController.java', [
  'Run Flow dry-run / trace again', 'NO_REQUESTED_SKILL'
]);
assertIncludes('lib/api/coreAdminApi.ts', ['getTaskCaseTimeline', 'CoreTaskCaseTimelineView']);
assertIncludes('components/tasks/TaskDetailView.tsx', [
  'TaskCaseTimelineRepairPanel', 'id: "debug"', 'StandardDispatchTimelinePanel', 'TaskPrimaryDiagnosisPanel'
]);
assertIncludes('lib/tasks/dispatchLifecycle.ts', [
  'NO_ACTIVE_FLOW_RULE', 'REQUIRED_CAPABILITY_MISSING', 'NO_ELIGIBLE_AGENT', 'buildStandardDispatchTimeline'
]);

console.log('OK R8 case-timeline API remains support-compatible while Stage 5 uses one canonical blocker and formal Event-to-Result timeline for standard operators.');
