import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const read = (relative: string) => fs.readFileSync(path.join(root, relative), 'utf8');

test('C12-HF1 closes known Admin UI typecheck regressions', () => {
  const convergence = read('components/capabilities/CaseConvergenceConsole.tsx');
  assert.match(convergence, /useEffect, useMemo, useRef, useState/);

  const sidebar = read('components/layout/Sidebar.tsx');
  assert.equal(sidebar.includes('defaultOpen='), false);
  assert.match(sidebar, /<details[^>]+open=/);

  const navigation = read('components/tasks/product/TaskDetailSectionNavigation.tsx');
  assert.match(navigation, /type TaskDetailSectionLabelKey/);
  assert.match(navigation, /sectionLabelKey\(id: TaskDetailSectionId\): TaskDetailSectionLabelKey/);

  const a2aApi = read('lib/api/domains/a2aOperationsApi.ts');
  assert.match(a2aApi, /const query: Record<string, string \| number \| boolean \| null \| undefined> = \{ \.\.\.filters \};/);

  const lifecycle = read('lib/tasks/dispatchLifecycle.ts');
  assert.match(lifecycle, /const noRuleCodes = new Set/);
  assert.match(lifecycle, /code = 'NO_MATCHING_RULE'/);

  const tsconfig = JSON.parse(read('tsconfig.json')) as { compilerOptions?: { allowImportingTsExtensions?: boolean } };
  assert.equal(tsconfig.compilerOptions?.allowImportingTsExtensions, true);
});
