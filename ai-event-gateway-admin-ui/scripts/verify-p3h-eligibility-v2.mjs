import fs from 'node:fs';

const requiredFiles = [
  '../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityServiceV2.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityV2Response.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityV2Candidate.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityV2BlockingReason.java',
  './docs/P3_H_ELIGIBILITY_V2_SHADOW.md'
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) {
    throw new Error(`Missing required P3-H file: ${file}`);
  }
}

const service = fs.readFileSync('../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityServiceV2.java', 'utf8');
for (const token of [
  'P3H_NO_ACTIVE_POLICY_SCOPE',
  'P3H_REQUIRED_CAPABILITY_MISSING',
  'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
  'P3H_QUALITY_RULE_FAILED',
  'DispatchEligibilityV2ScoreBreakdown',
  'setEngineMode'
]) {
  if (!service.includes(token)) {
    throw new Error(`DispatchEligibilityServiceV2 missing token: ${token}`);
  }
}

const controller = fs.readFileSync('../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchEligibilityController.java', 'utf8');
if (!controller.includes('/tasks/{taskId}/eligible-agents-v2')) {
  throw new Error('DispatchEligibilityController missing /eligible-agents-v2 endpoint');
}

const api = fs.readFileSync('lib/api/coreAdminApi.ts', 'utf8');
if (!api.includes('getTaskEligibleAgentsV2')) {
  throw new Error('coreAdminApi missing getTaskEligibleAgentsV2');
}

const types = fs.readFileSync('lib/types/core.ts', 'utf8');
for (const token of ['CoreDispatchEligibilityV2Response', 'CoreDispatchEligibilityV2Candidate', 'CoreDispatchEligibilityV2BlockingReason']) {
  if (!types.includes(token)) throw new Error(`core.ts missing ${token}`);
}

const taskDetail = fs.readFileSync('components/tasks/TaskDetailView.tsx', 'utf8');
if (!taskDetail.includes('P3-H · Eligibility V2 Shadow')) {
  throw new Error('TaskDetailView missing P3-H shadow panel');
}

console.log('OK P3-H Eligibility V2 shadow foundation verified.');
