import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

import { taskDiagnosisCatalogEntries, taskDiagnosisCatalogEntry } from '../lib/tasks/taskDiagnosisCatalog.ts';
import { deriveAllowedTaskRemediationCommands } from '../lib/tasks/taskRemediationCommands.ts';
import type { CoreTaskRuntimeView } from '../lib/types/core';

const bannedGenericCopy = [
  'Review the configuration and try again.',
  'Dispatch information',
  'Waiting for the next state transition.',
  'Operation failed.',
];

test('C8 diagnosis catalog provides operator-specific title, owner, evidence, and recommended command', () => {
  const noFlow = taskDiagnosisCatalogEntry('NO_MATCHING_FLOW');
  assert.equal(noFlow.title, 'No matching active Source Flow');
  assert.equal(noFlow.ownerPlane, 'CORE_CONFIGURATION');
  assert.equal(noFlow.evidencePointer, 'ROUTING / FLOW');
  assert.equal(noFlow.recommendedCommand, 'REEVALUATE_ROUTING');

  const delivery = taskDiagnosisCatalogEntry('DISPATCH_DELIVERY_FAILED');
  assert.equal(delivery.ownerPlane, 'NETTY_RUNTIME');
  assert.equal(delivery.recommendedCommand, 'RETRY_DELIVERY');

  const rendered = JSON.stringify(taskDiagnosisCatalogEntries());
  for (const generic of bannedGenericCopy) assert.equal(rendered.includes(generic), false);
});

test('C8 remediation vocabulary declares an explicit operation scope', () => {
  const task = { taskId: 'task-c8', status: 'FAILED', version: 12 } as CoreTaskRuntimeView;
  const definitions = deriveAllowedTaskRemediationCommands(task);
  const byType = new Map(definitions.map((definition) => [definition.commandType, definition]));
  assert.equal(byType.get('RETRY_TASK')?.scope, 'TASK');
  assert.equal(byType.get('ASSIGN_AGENT')?.scope, 'ASSIGNMENT');
  assert.equal(byType.get('IGNORE_TASK')?.scope, 'TASK');
  assert.equal(byType.get('CANCEL_TASK')?.riskLevel, 'HIGH');
});

test('C8 Task detail uses five real sections and one governed mutation surface', () => {
  const root = path.resolve(import.meta.dirname, '..');
  const navigation = fs.readFileSync(path.join(root, 'components/tasks/product/TaskDetailSectionNavigation.tsx'), 'utf8');
  const detail = fs.readFileSync(path.join(root, 'components/tasks/TaskDetailView.tsx'), 'utf8');
  const toolbar = fs.readFileSync(path.join(root, 'components/tasks/TaskCapabilityToolbar.tsx'), 'utf8');

  assert.equal((navigation.match(/id: 'task-/g) ?? []).length, 5);
  for (const ghost of ['task-related', 'task-issues', 'task-sync', 'task-audit']) assert.equal(navigation.includes(ghost), false);
  assert.equal(detail.includes(`id="task-related"`), false);
  assert.equal(detail.includes(`id="task-issues"`), false);
  assert.equal(detail.includes(`id="task-sync"`), false);
  assert.equal(detail.includes(`id="task-audit"`), false);
  assert.equal(detail.includes('TaskPrimaryDiagnosisPanel'), false);
  assert.match(detail, /onCommand=\{\(command\) => openRemediationCommand\(command\.commandType\)\}/);
  assert.doesNotMatch(detail, /setPendingAction\("cancel"\)|setPendingAction\("reassign"\)/);
  assert.doesNotMatch(toolbar, /Retry dispatch|Reassign|Cancel task/);
  assert.match(toolbar, /More Task actions/);
});

test('C8 Task operational source no longer uses the four generic diagnosis phrases', () => {
  const root = path.resolve(import.meta.dirname, '..');
  const sources = [path.join(root, 'lib/tasks'), path.join(root, 'components/tasks')];
  const files: string[] = [];
  const walk = (dir: string) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (/\.(ts|tsx)$/.test(entry.name)) files.push(full);
    }
  };
  sources.forEach(walk);
  const rendered = files.map((file) => fs.readFileSync(file, 'utf8')).join('\n');
  for (const generic of bannedGenericCopy) assert.equal(rendered.includes(generic), false, generic);
});

test('C8 Failure Queue names recovery and dead-letter actions by lifecycle effect', () => {
  const root = path.resolve(import.meta.dirname, '..');
  const source = fs.readFileSync(path.join(root, 'components/tasks/TaskFailureQueuePanel.tsx'), 'utf8');
  assert.match(source, /Run Recovery Now/);
  assert.match(source, /Move to Dead Letter/);
  assert.doesNotMatch(source, />Manual Retry<|>DLQ<|Retry Dispatch/);
});
