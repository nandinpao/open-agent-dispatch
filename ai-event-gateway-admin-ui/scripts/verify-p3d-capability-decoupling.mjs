#!/usr/bin/env node
import { existsSync, readFileSync } from 'node:fs';

import { resolve } from 'node:path';

const cwd = process.cwd();
const root = cwd.endsWith('ai-event-gateway-admin-ui') ? resolve(cwd, '..') : cwd;
function read(relativePath) { return readFileSync(resolve(root, relativePath), 'utf8'); }
function exists(relativePath) { return existsSync(resolve(root, relativePath)); }

const checks = [
  ['migration V69 exists', 'ai-event-gateway-core/database-platform/src/main/resources/db/migration/V69__p3d_capability_decoupling_supply_semantics.sql', 'capability_task_legacy_migration_report'],
  ['domain model has semantic fields', 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentCapabilityCatalog.java', 'legacyTaskCoupling'],
  ['persistence PO has semantic fields', 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/database/persistence/agent/assignment/po/AgentCapabilityCatalogPo.java', 'capabilityType'],
  ['mybatis maps semantic fields', 'ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent.assignment/AgentAssignmentDao.xml', 'capability_type'],
  ['service no longer throws task definition required for capability', 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java', 'LEGACY_TASK_COUPLED'],
  ['capability catalog UI uses supply semantics', 'ai-event-gateway-admin-ui/components/capabilities/CapabilityCatalogConsole.tsx', 'Capability 是供給側可重用服務能力'],
  ['capability relationship API panel exists', 'ai-event-gateway-admin-ui/components/relations/CapabilityRelationshipPanel.tsx', 'P3-D keeps it visible for migration'],
  ['P3-D documentation exists', 'ai-event-gateway-admin-ui/docs/P3_D_CAPABILITY_DECOUPLING.md', 'Capability Decoupling'],
];

let failed = false;
for (const [label, file, needle] of checks) {
  if (!exists(file)) {
    console.error(`[FAIL] ${label}: missing ${file}`);
    failed = true;
    continue;
  }
  const text = read(file);
  if (!text.includes(needle)) {
    console.error(`[FAIL] ${label}: missing marker ${needle}`);
    failed = true;
    continue;
  }
  console.log(`[OK] ${label}`);
}

const capabilityUi = read('ai-event-gateway-admin-ui/components/capabilities/CapabilityCatalogConsole.tsx');
if (capabilityUi.includes('getDispatchTaskDefinitions') || capabilityUi.includes('Task Definition<select')) {
  console.error('[FAIL] Capability Catalog UI still exposes Task Definition selection.');
  failed = true;
} else {
  console.log('[OK] Capability Catalog UI no longer exposes Task Definition picker');
}

process.exit(failed ? 1 : 0);
