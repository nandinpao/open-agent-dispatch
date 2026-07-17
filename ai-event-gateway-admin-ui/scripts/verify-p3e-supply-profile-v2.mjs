#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const repoRoot = path.resolve(root, '..');
const checks = [
  ['P3-E migration', path.join(repoRoot, 'ai-event-gateway-core/database-platform/src/main/resources/db/migration/V70__p3e_supply_profile_v2.sql'), ['create table if not exists supply_profiles', 'supply_profile_legacy_migration_report']],
  ['SupplyProfile domain', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/SupplyProfile.java'), ['runtimeBindingId', 'capabilitySnapshot', 'qualitySnapshot']],
  ['SupplyProfile persistence PO', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/assignment/po/SupplyProfilePo.java'), ['capabilitySnapshotJson', 'runtimeFeatureSnapshotJson']],
  ['Repository contract', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentRepository.java'), ['saveSupplyProfile', 'searchSupplyProfiles']],
  ['Service API', path.join(repoRoot, 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java'), ['P3E_RUNTIME_BINDING_REQUIRED', 'normalizeSupplyProfileStatus']],
  ['Controller API', path.join(repoRoot, 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java'), ['/supply-profiles', '/agents/{agentId}/supply-profiles']],
  ['Admin API endpoints', path.join(root, 'lib/api/endpoints.ts'), ['supplyProfiles', 'agentSupplyProfiles']],
  ['Admin API client', path.join(root, 'lib/api/coreAdminApi.ts'), ['getSupplyProfiles', 'getAgentSupplyProfiles', 'upsertSupplyProfile']],
  ['UI type', path.join(root, 'lib/types/core.ts'), ['CoreSupplyProfile', 'CoreSupplyProfileStatus']],
  ['Agent Hub panel', path.join(root, 'components/relations/SupplyProfileRelationshipPanel.tsx'), ['getSupplyProfiles', 'runtimeBindingId']],
  ['Supply Profiles console', path.join(root, 'components/supply-profiles/SupplyProfilesConsole.tsx'), ['Binding-backed Supply Profiles', 'Legacy Assignment Profiles']],
  ['P3-E doc', path.join(root, 'docs/P3_E_SUPPLY_PROFILE_V2.md'), ['Supply Profile v2', 'legacy Assignment Profile']],
];

let failed = false;
for (const [label, file, patterns] of checks) {
  if (!fs.existsSync(file)) {
    console.error(`[FAIL] ${label}: missing ${file}`);
    failed = true;
    continue;
  }
  const text = fs.readFileSync(file, 'utf8');
  for (const pattern of patterns) {
    if (!text.includes(pattern)) {
      console.error(`[FAIL] ${label}: missing pattern ${pattern}`);
      failed = true;
    }
  }
  if (!failed) console.log(`[OK] ${label}`);
}

if (failed) process.exit(1);
console.log('[OK] P3-E Supply Profile v2 verification completed');
