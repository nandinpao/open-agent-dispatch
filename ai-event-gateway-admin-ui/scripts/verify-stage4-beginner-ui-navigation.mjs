#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const uiRoot = process.cwd();
const repoRoot = path.resolve(uiRoot, '..');
function readUi(rel) {
  const file = path.join(uiRoot, rel);
  if (!fs.existsSync(file)) throw new Error(`Missing ${rel}`);
  return fs.readFileSync(file, 'utf8');
}
function readRepo(rel) {
  const file = path.join(repoRoot, rel);
  if (!fs.existsSync(file)) throw new Error(`Missing ${rel}`);
  return fs.readFileSync(file, 'utf8');
}
function includesAll(name, text, tokens) {
  const missing = tokens.filter((token) => !text.includes(token));
  if (missing.length) throw new Error(`${name} missing Stage 4 tokens:\n${missing.map((token) => ` - ${token}`).join('\n')}`);
}
function excludesAll(name, text, tokens) {
  const found = tokens.filter((token) => text.includes(token));
  if (found.length) throw new Error(`${name} still exposes retired workflow tokens:\n${found.map((token) => ` - ${token}`).join('\n')}`);
}

const navigation = readUi('lib/navigation/adminInformationArchitecture.ts');
const orderedHrefs = ['/dashboard', '/source-systems', '/agents', '/dispatch-flows', '/tasks', '/issues-events', '/settings'];
let lastIndex = -1;
for (const href of orderedHrefs) {
  const index = navigation.indexOf(`href: '${href}'`);
  if (index < 0) throw new Error(`Primary navigation missing ${href}`);
  if (index <= lastIndex) throw new Error(`Primary navigation order is invalid near ${href}`);
  lastIndex = index;
}
includesAll('navigation', navigation, [
  'export const adminAdvancedNavigation: AdminNavigationItem[] = [];',
  'export const adminDeveloperNavigation: AdminNavigationItem[] = [];',
  '來源系統',
  '唯一的派工設定入口',
]);
excludesAll('primary navigation', navigation, [
  "href: '/dispatch-governance'",
  "href: '/settings/task-types'",
  "href: '/settings/service-scopes'",
  "href: '/settings/capabilities'",
  "href: '/dispatch-readiness'",
]);

const sidebar = readUi('components/layout/Sidebar.tsx');
includesAll('Sidebar', sidebar, [
  'flex h-dvh w-72 flex-col overflow-hidden',
  'min-h-0 flex-1',
  'overflow-y-auto',
  'adminPrimaryNavigation.map',
  'Agent → 派工流程 → Task',
]);
excludesAll('Sidebar', sidebar, [
  'absolute bottom-',
  'max-h-[calc(100vh',
  'AdminUiModeSwitcher',
  'adminAdvancedNavigation.map',
  'adminDeveloperNavigation.map',
]);

const topbar = readUi('components/layout/Topbar.tsx');
excludesAll('Topbar', topbar, ['AdminUiModeSwitcher', 'useAdminUiMode']);

const settings = readUi('app/settings/page.tsx');
includesAll('Settings page', settings, ['問題追蹤整合', 'Agent 註冊與核准', '安全事件']);
excludesAll('Settings page', settings, [
  'Dispatch Governance',
  'Legacy Task Types',
  'Legacy Service Scope',
  'Legacy Rule Diagnostics',
  'Legacy Capability / Skill Diagnostics',
  'Migration Readiness',
  'Release Cutover',
]);

const flowConsole = readUi('components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx');
includesAll('Dispatch Flow beginner console', flowConsole, [
  '來源系統',
  '唯一的派工設定入口',
  'sourceSystems.map',
  'agents.map',
  'capabilities.map',
  'type="checkbox"',
  'list="stage4-object-types"',
  'list="stage4-event-types"',
  'list="stage4-error-codes"',
  'coreAdminApi.createDispatchFlow',
  'coreAdminApi.updateDispatchFlow',
  "candidatePoolMode: 'EXPLICIT_FLOW_AGENTS'",
  'requestedSkill: undefined',
  '特殊能力（選填）',
  '真正的 Event → Task → Agent 派送由正常 Task 流程驗證',
]);
excludesAll('Dispatch Flow beginner console', flowConsole, [
  'coreAdminApi.upsertDispatchFlowRule(',
  'coreAdminApi.upsertDispatchFlowSkill(',
  'coreAdminApi.upsertDispatchFlowAgent(',
  'coreAdminApi.dryRunDispatchFlow(',
  'coreAdminApi.createDispatchContractTestTask(',
  'coreAdminApi.testDispatchFlowExternal(',
  'TestDispatchReadiness',
  '<FlowOwnedAgentAssignmentPanel',
]);


const sourcePage = readUi('app/source-systems/page.tsx');
includesAll('Source Systems page', sourcePage, ['SourceSystemConsole']);
const sourceConsole = readUi('components/source-systems/SourceSystemConsole.tsx');
includesAll('Source Systems console', sourceConsole, [
  'getDispatchContractSourceSystems',
  'getDispatchFlows',
  '系統不會根據 CMS、MES、ERP 等名稱套用特殊派工邏輯',
  'createFlowHref',
  '來源只代表事件從哪個系統進來',
]);
excludesAll('Source Systems console', sourceConsole, [
  'dispatchGovernanceApi',
  'Service Scope',
  'Assignment Profile',
  'Flow Participation',
]);

const agentsPage = readUi('app/agents/page.tsx');
includesAll('Agents page', agentsPage, [
  'Agent 標準操作捷徑',
  '/source-systems',
  '/dispatch-flows',
  '/tasks',
]);
excludesAll('Agents page', agentsPage, [
  '/settings/capabilities',
  '/settings/dispatch-task-definitions',
  'task scope',
  'InformationArchitectureGuide',
]);

const agentHook = readUi('hooks/useAgentDetail.ts');
includesAll('Agent Detail hook', agentHook, [
  'dispatchFlows: CoreDispatchFlowView[];',
  'coreAdminApi.getDispatchFlows(scopedTenantId)',
  'flow.agents',
  'agent.agentId === agentId',
]);

const agentDetail = readUi('components/agents/AgentDetailProductView.tsx');
includesAll('Agent Detail', agentDetail, [
  'AgentDispatchFlowsPanel',
  'data.dispatchFlows',
  "id: 'flows'",
  "label: '派工流程'",
  '/dispatch-flows',
]);
excludesAll('Agent Detail', agentDetail, [
  'Test Dispatch Readiness',
  '<AgentFlowParticipationPanel',
  '<QuickTaskScopeDialog',
  '<QuickDispatchRuleDialog',
  "id: 'rules'",
  "label: 'Rules'",
]);

const packageJson = JSON.parse(readUi('package.json'));
if (!packageJson.scripts['verify:stage4-beginner-ui']) throw new Error('package.json missing verify:stage4-beginner-ui');
if (!packageJson.scripts['test:stage4-beginner-ui']) throw new Error('package.json missing test:stage4-beginner-ui');

const makefile = readRepo('Makefile');
includesAll('Makefile', makefile, [
  'verify-stage4-beginner-ui-navigation',
  'test-stage4-admin-ui',
  'stage4-beginner-ui-navigation',
]);

console.log('OK Stage 4 beginner navigation, source systems entry, scroll-safe sidebar, aggregate Flow form, optional Capability selection, and shared Agent/Flow relation contract are present.');
