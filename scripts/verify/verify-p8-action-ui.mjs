#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const globalRoot = execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim();
const require = createRequire(import.meta.url);
const ts = require(path.join(globalRoot, 'typescript'));
const files = [
  'ai-event-gateway-admin-ui/lib/types/dispatchGovernance.ts',
  'ai-event-gateway-admin-ui/lib/api/dispatchGovernanceApi.ts',
  'ai-event-gateway-admin-ui/lib/server/backendProxy.ts',
  'ai-event-gateway-admin-ui/components/dispatch-governance/ActionGovernancePanel.tsx',
  'ai-event-gateway-admin-ui/components/dispatch-governance/DispatchGovernanceConsole.tsx',
];

let failed = false;
for (const relative of files) {
  const source = fs.readFileSync(path.join(ROOT, relative), 'utf8');
  const result = ts.transpileModule(source, {
    fileName: relative,
    reportDiagnostics: true,
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.ESNext,
      moduleResolution: ts.ModuleResolutionKind.Bundler,
      jsx: ts.JsxEmit.Preserve,
      strict: true,
      isolatedModules: true,
    },
  });
  const errors = (result.diagnostics ?? []).filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error);
  if (errors.length) {
    failed = true;
    console.error(`\n${relative}`);
    for (const diagnostic of errors) {
      console.error(ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
    }
  }
}
if (failed) process.exit(1);
console.log(`[PASS] P8 Action Governance TypeScript/TSX syntax transpile (${files.length} files).`);
