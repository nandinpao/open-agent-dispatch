#!/usr/bin/env node
import fs from 'node:fs';

function read(path) {
  if (!fs.existsSync(path)) throw new Error(`Missing required file: ${path}`);
  return fs.readFileSync(path, 'utf8');
}
function requireIncludes(label, content, tokens) {
  for (const token of tokens) if (!content.includes(token)) throw new Error(`${label} missing token: ${token}`);
}
function requireExcludes(label, content, tokens) {
  for (const token of tokens) if (content.includes(token)) throw new Error(`${label} must not contain retired token: ${token}`);
}

const requiredFiles = [
  '../docs/R1_DISPATCH_FLOW_UI_IA/README.md',
  '../docs/R1_DISPATCH_FLOW_UI_IA/r1_change_log.md',
  '../docs/R1_DISPATCH_FLOW_UI_IA/r1_to_r2_handoff.md',
  'docs/R1_DISPATCH_FLOW_UI_IA.md',
  'app/dispatch-flows/page.tsx',
  'app/dispatch-capabilities/page.tsx',
  'app/dispatch-contracts/page.tsx',
  'lib/navigation/adminInformationArchitecture.ts',
  'components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx',
  'scripts/route-smoke.mjs',
  'package.json',
];
for (const file of requiredFiles) read(file);

const nav = read('lib/navigation/adminInformationArchitecture.ts');
requireIncludes('adminInformationArchitecture', nav, ["href: '/dispatch-flows'", "label: t('nav.dispatchFlows')", '唯一的派工設定入口']);
const primaryOrder = ["href: '/dashboard'", "href: '/agents'", "href: '/dispatch-flows'", "href: '/tasks'", "href: '/issues-events'", "href: '/settings'"];
let lastIndex = -1;
for (const token of primaryOrder) {
  const index = nav.indexOf(token);
  if (index === -1) throw new Error(`Primary navigation missing ${token}`);
  if (index < lastIndex) throw new Error(`Primary navigation order is wrong around ${token}`);
  lastIndex = index;
}
requireIncludes('Stage 4 navigation', nav, ['adminAdvancedNavigation: AdminNavigationItem[] = []', 'adminDeveloperNavigation: AdminNavigationItem[] = []']);
requireExcludes('Primary navigation', nav, ["href: '/dispatch-capabilities'", "href: '/dispatch-governance'", "href: '/dispatch-readiness'"]);

const flowsPage = read('app/dispatch-flows/page.tsx');
requireIncludes('dispatch-flows page', flowsPage, ['DispatchContractBuilderConsole', 'DispatchFlowsPage']);
const capabilitiesPage = read('app/dispatch-capabilities/page.tsx');
requireIncludes('dispatch-capabilities compatibility route', capabilitiesPage, ['redirect(buildDispatchFlowsHref', "'/dispatch-flows'"]);
const contractsPage = read('app/dispatch-contracts/page.tsx');
requireIncludes('dispatch-contracts compatibility route', contractsPage, ['redirect(buildDispatchFlowsHref', "'/dispatch-flows'"]);

const builder = read('components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx');
requireIncludes('Dispatch Flow console', builder, ['唯一的派工設定入口', '建立派工流程', 'coreAdminApi.createDispatchFlow', 'coreAdminApi.updateDispatchFlow']);
requireExcludes('Dispatch Flow console', builder, ['<h1 className="mt-1 text-2xl font-black text-slate-950">Dispatch Capabilities</h1>']);

const routeSmoke = read('scripts/route-smoke.mjs');
requireIncludes('route smoke', routeSmoke, ["'/dispatch-flows'"]);
const pkg = JSON.parse(read('package.json'));
if (!pkg.scripts?.['verify:r1-dispatch-flow-ui-ia']) throw new Error('package.json missing verify:r1-dispatch-flow-ui-ia script');
console.log('OK R1 compatibility routes and the Stage 4 primary Dispatch Flow information architecture are present.');
