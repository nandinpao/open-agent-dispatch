import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(process.cwd());
function read(relativePath: string): string {
  return fs.readFileSync(path.join(ROOT, relativePath), 'utf8');
}

describe('Stage 7 Legacy isolation', () => {
  it('does not recreate a SUPPORT Role authorization path in the product shell', () => {
    const shell = read('components/layout/AppShell.tsx');
    assert.doesNotMatch(shell, /SUPPORT_ONLY_PREFIXES/);
    assert.doesNotMatch(shell, /hasRole\(/);
    assert.doesNotMatch(shell, /roles\.includes\(/);
  });

  it('keeps historical routes out of the standard sidebar', () => {
    const sidebar = read('components/layout/Sidebar.tsx');
    for (const route of [
      '/assignment-profiles',
      '/supply-profiles',
      '/dispatch-policies',
      '/settings/dispatch-governance',
      '/testing/dispatch-readiness',
      '/testing/dispatch-simulator',
    ]) {
      assert.doesNotMatch(sidebar, new RegExp(route.replaceAll('/', '\\/')));
    }
  });

  it('guards the retained simulator compatibility route with canonical UI entitlements', () => {
    const page = read('app/testing/dispatch-simulator/page.tsx');
    assert.match(page, /EntitlementPageGuard/);
    assert.match(page, /featureId="dispatch"/);
  });

  it('does not expose a legacy mutation workflow from the standard shell', () => {
    const shell = read('components/layout/AppShell.tsx');
    assert.doesNotMatch(shell, /createAssignmentProfile|saveServiceScope|approveQualification/);
  });
});
