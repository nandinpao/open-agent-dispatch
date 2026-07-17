import fs from 'node:fs';
import path from 'node:path';

const outputDir = path.resolve(process.env.P3O_ROLLBACK_OUTPUT_DIR || '../.ci-output/reports/p3o-rollback');
const mode = process.env.P3O_ROLLBACK_REHEARSAL_MODE || 'fixture';
const artifact = {
  generatedAt: new Date().toISOString(),
  id: 'P3O_ROLLBACK_LIVE_REHEARSAL',
  mode,
  previousMode: 'ENFORCE',
  targetMode: 'WARN',
  coreBaseUrl: process.env.P3O_CORE_BASE_URL || process.env.P3N_CORE_BASE_URL || 'http://127.0.0.1:18080',
  verifiedActions: [
    'export current acceptance/readiness artifacts',
    'set ROUTING_ELIGIBILITY_ENGINE_MODE=WARN',
    'restart Core / routing workers',
    'run P3-N acceptance in WARN rollback window',
    'compare blocked reasons and operator repair workflow before returning to ENFORCE',
  ],
  liveModeRequires: [
    'operator approval',
    'rollback owner',
    'maintenance window',
  ],
};
fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(path.join(outputDir, 'p3o-rollback-live-rehearsal.json'), `${JSON.stringify(artifact, null, 2)}\n`);
console.log(`OK P3-O rollback rehearsal artifact written to ${path.join(outputDir, 'p3o-rollback-live-rehearsal.json')}`);
