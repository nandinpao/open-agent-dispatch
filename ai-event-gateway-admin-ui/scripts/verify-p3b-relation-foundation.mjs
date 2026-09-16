#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const requiredFiles = [
  'components/relations/types.ts',
  'components/relations/RelationPickerModal.tsx',
  'components/relations/RelationPreviewDrawer.tsx',
  'components/relations/FieldManagementLink.tsx',
  'components/relations/RelationshipCardList.tsx',
  'components/relations/RelationshipManagementPanel.tsx',
  'components/relations/ReadinessChecklist.tsx',
  'components/relations/relationPresets.ts',
  'components/relations/index.ts',
  'docs/P3_B_RELATION_PICKER_PREVIEW_FOUNDATION.md',
];

const requiredContent = [
  ['app/agents/page.tsx', 'RuntimeResourceRelationshipPanel'],
  ['app/agents/page.tsx', 'CapabilityRelationshipPanel'],
  ['app/agents/page.tsx', 'SupplyProfileRelationshipPanel'],
  ['app/dispatch-policies/page.tsx', 'taskDefinitionSource'],
  ['app/dispatch-policies/page.tsx', 'runtimeFeatureSource'],
  ['app/dispatch-policies/page.tsx', 'qualityRuleSource'],
  ['app/tasks/page.tsx', 'dispatchPolicySource'],
  ['components/relations/relationPresets.ts', 'CMS_CONTENT_READ'],
  ['components/relations/relationPresets.ts', 'CMS_CONTENT_REVIEW'],
  ['components/relations/relationPresets.ts', 'Looks like a Task Type'],
  ['components/relations/CapabilityRelationshipPanel.tsx', 'Capability API unavailable'],
];

let ok = true;
for (const file of requiredFiles) {
  const target = path.join(root, file);
  if (!fs.existsSync(target)) {
    console.error(`[FAIL] Missing required file: ${file}`);
    ok = false;
  } else {
    console.log(`[OK] ${file}`);
  }
}

for (const [file, needle] of requiredContent) {
  const target = path.join(root, file);
  const body = fs.existsSync(target) ? fs.readFileSync(target, 'utf8') : '';
  if (!body.includes(needle)) {
    console.error(`[FAIL] ${file} missing content: ${needle}`);
    ok = false;
  } else {
    console.log(`[OK] ${file} contains ${needle}`);
  }
}

if (!ok) {
  process.exit(1);
}

console.log('\nP3-B relation foundation verification completed.');
