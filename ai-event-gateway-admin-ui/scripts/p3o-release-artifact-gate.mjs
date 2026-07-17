import fs from 'node:fs';
import path from 'node:path';

const archiveDir = path.resolve(process.env.P3O_RELEASE_ARTIFACT_ARCHIVE_DIR || '../.ci-output/release-artifacts/p3o-enforce');
const allowFixture = process.env.P3O_ALLOW_FIXTURE_ARTIFACT === 'true';
const requireLive = process.env.P3O_REQUIRE_LIVE_ACCEPTANCE !== 'false';
const manifestFile = path.join(archiveDir, 'p3o-release-artifact-manifest.json');
const summaryFile = path.join(archiveDir, 'p3n-full-enforce-summary.json');
const readinessFile = path.join(archiveDir, 'p3n-readiness-report.json');
const acceptanceFile = path.join(archiveDir, 'p3n-enforce-runtime-acceptance.json');

function readJson(file) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch (error) { throw new Error(`Cannot read JSON artifact ${file}: ${error.message}`); }
}

const manifest = readJson(manifestFile);
const summary = readJson(summaryFile);
const readiness = readJson(readinessFile);
const acceptance = readJson(acceptanceFile);
const blocking = [];
if (!manifest.enforceReady) blocking.push('P3-O manifest enforceReady=false');
if (!summary.enforceReady) blocking.push('P3-N summary enforceReady=false');
if (!readiness.enforceReady || (readiness.summary?.blocking ?? 1) !== 0) blocking.push('Migration readiness is not clean.');
const mode = summary.mode ?? acceptance.mode ?? acceptance.fixtureMode ?? manifest.mode;
if (requireLive && !allowFixture && mode !== 'live') blocking.push(`Live acceptance artifact required, but artifact mode=${mode}. Set P3O_ALLOW_FIXTURE_ARTIFACT=true only for dry-run verification.`);

if (blocking.length > 0) {
  console.error('[P3-O] Release artifact gate failed:');
  for (const item of blocking) console.error(`- ${item}`);
  process.exit(1);
}
console.log(`OK P3-O release artifact gate passed with mode=${mode}, archive=${archiveDir}`);
