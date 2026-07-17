#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const failures = [];
const expectIncludes = (file, needle, label = needle) => {
  const content = read(file);
  if (!content.includes(needle)) failures.push(`${file}: missing ${label}`);
};
const expectNotIncludes = (file, needle, label = needle) => {
  const content = read(file);
  if (content.includes(needle)) failures.push(`${file}: unexpected ${label}`);
};

expectIncludes('components/agents/AgentDetailProductView.tsx', "qualification.qualificationStatus === 'APPROVED'", 'dispatch rule candidates use approved task scopes');
expectIncludes('components/agents/AgentDetailProductView.tsx', "'PENDING'].includes(status)", 'Task Scope table exposes approve action for PENDING scope');
expectIncludes('components/agents/AgentDetailProductView.tsx', 'Approve immediately', 'inline Assign Task Scope supports immediate approval');
expectIncludes('components/agents/AgentDetailProductView.tsx', 'Assign & Approve Task Scope', 'primary CTA makes activation explicit');
expectIncludes('hooks/useAgentDetail.ts', 'Agent Service Scope ${saved.profileCode} is ${saved.qualificationStatus}', 'Agent Detail command result uses Service Scope wording');
expectIncludes('lib/types/core.ts', 'autoApprove?: boolean', 'Admin UI qualification command supports autoApprove');
expectIncludes('../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentQualificationCommand.java', 'private boolean autoApprove', 'Core command supports autoApprove');
expectIncludes('../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java', 'if (request.isAutoApprove())', 'Core assignment service can promote pending scope to approved');
expectIncludes('lib/dispatch-readiness/beginnerWorkflow.ts', 'Agent Service Scope and Dispatch Rule are active', 'readiness wording points to service scope gate');
expectIncludes('../ai-event-gateway-netty/scripts/cluster-run-many-agents.sh', 'CORE_BOOTSTRAP_AUTO_APPROVE_QUALIFICATION:-true', 'cluster bootstrap auto-approves service scopes for local/SIT by default');
expectNotIncludes('components/agents/AgentDetailProductView.tsx', 'Profile capabilities vs runtime-reported capabilities', 'duplicate capability comparison panel stays removed');

if (failures.length) {
  console.error('[stage18] Service Scope activation verification failed:');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}
console.log('[stage18] Service Scope activation verification passed.');
