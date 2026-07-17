import { existsSync, readFileSync } from 'node:fs';
import assert from 'node:assert/strict';

const requiredFiles = [
  'lib/dashboard/agentMerge.ts',
  'components/agents/AgentDetailView.tsx',
  'components/agents/AgentGovernanceWorkflowActions.tsx',
  'tests/runtime-duplicates.test.ts'
];
for (const file of requiredFiles) {
  assert.equal(existsSync(file), true, `missing ${file}`);
}

const merge = readFileSync('lib/dashboard/agentMerge.ts', 'utf8');
assert.match(merge, /duplicateRuntimeDetected/);
assert.match(merge, /summarizeRuntimes/);

const detail = readFileSync('components/agents/AgentDetailView.tsx', 'utf8');
assert.match(detail, /Runtime Sessions \/ Duplicate Detection/);
assert.match(detail, /Disconnect All Sessions/);

const actions = readFileSync('components/agents/AgentGovernanceWorkflowActions.tsx', 'utf8');
assert.match(actions, /Disconnect All/);
assert.match(actions, /disconnectAllAgentSessions/);

console.log('P5 integration check passed.');
