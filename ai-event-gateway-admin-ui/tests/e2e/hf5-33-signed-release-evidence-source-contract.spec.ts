import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { test, expect } from '@playwright/test';

const root = join(__dirname, '..', '..');
const read = (path: string) => readFileSync(join(root, path), 'utf8');

test('release certification cannot declare browser PASS claims', () => {
  const ui = read('components/release-certification/ReleaseCertificationConsole.tsx');
  expect(ui).not.toContain("sourceGateStatus: 'PASS'");
  expect(ui).not.toContain("buildStatus: 'PASS'");
  expect(ui).not.toContain('certificationEvidenceRef:');
  expect(ui).toContain('signed_evidence_ready');
  expect(ui).toContain('SOURCE_GATE');
  expect(ui).toContain('BUILD_GATE');
  expect(ui).toContain('RUNTIME_GATE');
  expect(ui).toContain('E2E_GATE');
});

test('admin API exposes signed evidence authority rather than client gate claims', () => {
  const api = read('lib/api/coreAdminApi.ts');
  const endpoints = read('lib/api/endpoints.ts');
  for (const token of ['getProductionReleaseTrustedExecutors', 'registerProductionReleaseTrustedExecutor', 'revokeProductionReleaseTrustedExecutor', 'ingestProductionReleaseSignedAttestation', 'getProductionReleaseCandidateEvidence']) {
    expect(api).toContain(token);
  }
  for (const token of ['productionFoundationTrustedExecutors', 'productionFoundationSignedAttestations', 'productionFoundationCandidateEvidence']) {
    expect(endpoints).toContain(token);
  }
});

test('legacy release console is read-only retired surface', () => {
  const legacy = read('components/release/ProductionFoundationReleaseConsole.tsx');
  expect(legacy).toContain('Legacy release console retired');
  expect(legacy).toContain('/settings/release-certification');
  expect(legacy).not.toContain('createProductionReleaseCandidate(');
  expect(legacy).not.toContain('setProductionFoundationFlowAuthority(');
});
