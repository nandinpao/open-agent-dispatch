import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = resolve(__dirname, '../..');
const source = readFileSync(resolve(ROOT, 'components/release-certification/ReleaseCertificationConsole.tsx'), 'utf8');
const api = readFileSync(resolve(ROOT, 'lib/api/coreAdminApi.ts'), 'utf8');

test('HF5.32C release UI separates readiness from actual Flow authority', async () => {
  expect(source).toContain('Release readiness');
  expect(source).toContain('Actual Flow authority');
  expect(source).toContain('promoted');
  expect(source).toContain('release_ready');
});

test('HF5.32C cutover uses canonical Flow selection instead of free-text Flow ID', async () => {
  expect(source).toContain('Canonical Flow<select');
  expect(source).toContain('Select Flow');
  expect(source).not.toContain('Flow ID<input');
});

test('HF5.32C dangerous mutations require preflight and exact confirmation', async () => {
  for (const token of ['Review activation', 'Review revocation', 'Review Flow authority change', 'confirmationPhrase']) {
    expect(source).toContain(token);
  }
  expect(api).toContain('preflightProductionReleaseCandidate');
  expect(api).toContain('preflightProductionFoundationFlow');
  expect(api).toContain('promoteProductionFoundationFlow');
  expect(api).toContain('rollbackProductionFoundationFlow');
});

test('HF5.32C UI uses dedicated Release permissions', async () => {
  for (const permission of [
    'release.certification.read',
    'release.candidate.create',
    'release.candidate.certify',
    'release.candidate.activate',
    'release.candidate.revoke',
    'release.flow.preflight',
    'release.flow.promote',
    'release.flow.rollback',
  ]) expect(source).toContain(permission);
});
