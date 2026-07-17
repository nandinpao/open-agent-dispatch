import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(process.cwd(), '..');
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function requireIncludes(name, text, tokens) {
  const missing = tokens.filter((token) => !text.includes(token));
  if (missing.length) throw new Error(`${name} missing expected tokens:\n${missing.map((token) => ` - ${token}`).join('\n')}`);
}
function requireExcludes(name, text, tokens) {
  const found = tokens.filter((token) => text.includes(token));
  if (found.length) throw new Error(`${name} contains retired workflow tokens:\n${found.map((token) => ` - ${token}`).join('\n')}`);
}
function requireFile(rel) {
  const abs = path.join(root, rel);
  if (!fs.existsSync(abs)) throw new Error(`Missing file: ${rel}`);
  return fs.readFileSync(abs, 'utf8');
}

const migration = requireFile('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V98__r5_flow_owned_agent_assignment.sql');
requireIncludes('R5 migration', migration, ['flow_agent_assignments', 'event_stage', 'agent_role', 'dispatch_r5_flow_agent_readiness_report']);
const flowDto = requireFile('ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowView.java');
requireIncludes('DispatchFlowView', flowDto, ['List<DispatchFlowAgentView> agents', 'getAgents()', 'setAgents']);
const coreTypes = read('ai-event-gateway-admin-ui/lib/types/core.ts');
requireIncludes('core types', coreTypes, ['CoreDispatchFlowAgentView', 'agents?: CoreDispatchFlowAgentView[]']);

const flowConsole = read('ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx');
requireIncludes('Stage 4 Flow Agent selection', flowConsole, [
  'agents.map', 'toggleAgent', 'selectedAgents', 'agents: selectedAgents', "candidatePoolMode: 'EXPLICIT_FLOW_AGENTS'", 'coreAdminApi.updateDispatchFlow',
]);
requireExcludes('Stage 4 Flow Agent selection', flowConsole, ['<FlowOwnedAgentAssignmentPanel', 'Assign Agent drawer skeleton', 'Save + readiness check in later phase']);

const hook = read('ai-event-gateway-admin-ui/hooks/useAgentDetail.ts');
requireIncludes('Agent Detail shared relation', hook, ['dispatchFlows: CoreDispatchFlowView[];', 'coreAdminApi.getDispatchFlows(scopedTenantId)', 'flow.agents', 'agent.agentId === agentId']);
const agentDetail = read('ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx');
requireIncludes('Agent Detail Flow relation', agentDetail, ['AgentDispatchFlowsPanel', 'data.dispatchFlows', "label: '派工流程'", '/dispatch-flows?agentId=']);
requireExcludes('Agent Detail Flow relation', agentDetail, ['AgentFlowParticipationPanel', 'R5 Flow Participation', "label: 'Flow Participation'"]);

for (const doc of ['docs/R5_FLOW_OWNED_AGENT_ASSIGNMENT/README.md', 'docs/R5_FLOW_OWNED_AGENT_ASSIGNMENT/r5_change_log.md', 'docs/R5_FLOW_OWNED_AGENT_ASSIGNMENT/r5_to_r6_handoff.md', 'ai-event-gateway-admin-ui/docs/R5_FLOW_OWNED_AGENT_ASSIGNMENT.md']) requireIncludes(doc, requireFile(doc), ['R5', 'Flow-owned Agent']);
requireIncludes('package.json', read('ai-event-gateway-admin-ui/package.json'), ['verify:r5-flow-owned-agent-assignment']);
requireIncludes('Makefile', read('Makefile'), ['verify-r5-flow-owned-agent-assignment']);
console.log('OK R5 persistence compatibility remains; Stage 4 uses one Flow-selected Agent relation and exposes it read-only from Agent Detail.');
