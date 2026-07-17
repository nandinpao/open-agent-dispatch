#!/usr/bin/env node
import fs from 'node:fs';

const required = [
  '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V68__p3c_runtime_resource_agent_runtime_binding.sql',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/RuntimeResource.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentRuntimeBinding.java',
  '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java',
  'components/runtime-resources/RuntimeResourceConsole.tsx',
  'app/settings/runtime-resources/page.tsx',
  'components/relations/RuntimeResourceRelationshipPanel.tsx',
  'docs/P3_C_RUNTIME_RESOURCE_AGENT_BINDING.md',
];

for (const path of required) {
  if (!fs.existsSync(path)) {
    console.error(`[FAIL] Missing ${path}`);
    process.exit(1);
  }
}

const checks = [
  ['../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V68__p3c_runtime_resource_agent_runtime_binding.sql', 'create table if not exists runtime_resources'],
  ['../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V68__p3c_runtime_resource_agent_runtime_binding.sql', 'create table if not exists agent_runtime_bindings'],
  ['../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V68__p3c_runtime_resource_agent_runtime_binding.sql', 'alter table agent_runtime_feature_trust add column if not exists binding_id'],
  ['../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java', '@GetMapping("/runtime-resources")'],
  ['../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java', '@GetMapping("/agents/{agentId}/runtime-bindings")'],
  ['../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java', 'RUNTIME_BINDING_ACTIVE'],
  ['../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentRuntimeFeatureTrust.java', 'private String bindingId'],
  ['lib/api/endpoints.ts', 'runtimeResources'],
  ['lib/api/coreAdminApi.ts', 'getRuntimeResources'],
  ['lib/types/core.ts', 'CoreAgentRuntimeBinding'],
  ['app/agents/page.tsx', 'RuntimeResourceRelationshipPanel'],
  ['lib/navigation/adminInformationArchitecture.ts', '/settings/runtime-resources'],
];

for (const [path, needle] of checks) {
  const text = fs.readFileSync(path, 'utf8');
  if (!text.includes(needle)) {
    console.error(`[FAIL] ${path} missing ${needle}`);
    process.exit(1);
  }
}

console.log('[OK] P3-C runtime resource / agent runtime binding foundation verified.');
