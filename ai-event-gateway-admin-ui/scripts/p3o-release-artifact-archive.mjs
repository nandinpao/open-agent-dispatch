import fs from 'node:fs';
import path from 'node:path';

const sourceDir = path.resolve(process.env.P3N_ACCEPTANCE_OUTPUT_DIR || '../.ci-output/reports/p3n-full-enforce');
const archiveDir = path.resolve(process.env.P3O_RELEASE_ARTIFACT_ARCHIVE_DIR || '../.ci-output/release-artifacts/p3o-enforce');
const requiredArtifacts = [
  'p3n-seed-report.json',
  'p3n-enforce-runtime-acceptance.json',
  'p3n-readiness-report.json',
  'p3n-rollback-rehearsal.json',
  'p3n-teardown-report.json',
  'p3n-full-enforce-summary.json',
];

function readJson(file) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return null; }
}

fs.mkdirSync(archiveDir, { recursive: true });
const copied = [];
const missing = [];
for (const name of requiredArtifacts) {
  const source = path.join(sourceDir, name);
  const target = path.join(archiveDir, name);
  if (!fs.existsSync(source)) {
    missing.push(name);
    continue;
  }
  fs.copyFileSync(source, target);
  copied.push(name);
}

const summary = readJson(path.join(archiveDir, 'p3n-full-enforce-summary.json'));
const readiness = readJson(path.join(archiveDir, 'p3n-readiness-report.json'));
const acceptance = readJson(path.join(archiveDir, 'p3n-enforce-runtime-acceptance.json'));
const manifest = {
  generatedAt: new Date().toISOString(),
  sourceDir,
  archiveDir,
  p3oPhase: 'P3-O ENFORCE Default Hardening & Legacy Runtime Removal',
  copied,
  missing,
  enforceReady: Boolean(summary?.enforceReady && readiness?.enforceReady),
  mode: summary?.mode ?? null,
  acceptanceMode: acceptance?.mode ?? acceptance?.fixtureMode ?? null,
  readinessBlocking: readiness?.summary?.blocking ?? null,
  requiredForRelease: true,
  notes: [
    'P3-O release artifact archive must be attached to release review before controlled ENFORCE.',
    'Live releases should have mode=live unless P3O_ALLOW_FIXTURE_ARTIFACT=true is explicitly set for dry-run validation.',
  ],
};
fs.writeFileSync(path.join(archiveDir, 'p3o-release-artifact-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

if (missing.length > 0 && process.env.P3O_ARCHIVE_STRICT === 'true') {
  console.error(`[P3-O] Missing required artifacts: ${missing.join(', ')}`);
  process.exit(1);
}
console.log(`OK P3-O archived ${copied.length} release artifacts to ${archiveDir}`);
