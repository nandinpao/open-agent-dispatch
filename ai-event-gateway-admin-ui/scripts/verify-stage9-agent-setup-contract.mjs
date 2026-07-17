import fs from 'node:fs';

const requiredFiles = [
  '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSetupController.java',
  '../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupRequest.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupResponse.java',
  '../ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/AgentSetupControllerMockMvcTest.java',
  '../ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/setup/AgentSetupServiceTest.java',
  '../scripts/acceptance/agent-setup-backend-contract-smoke.mjs',
  '../docs/postman/OpenDispatch_First_Agent_Setup.postman_collection.json',
  'tests/agent-setup-backend-contract.test.ts',
  'components/agents/AgentOnboardingPanel.tsx',
  'lib/api/coreAdminApi.ts',
  'lib/api/endpoints.ts',
  'lib/types/core.ts',
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing Stage 9 required file: ${file}`);
}

function read(file) {
  return fs.readFileSync(file, 'utf8');
}

const controller = read('../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSetupController.java');
for (const token of ['@PostMapping("/admin/agents/setup")', 'AgentSetupRequest', 'AgentSetupResponse', 'BAD_REQUEST']) {
  if (!controller.includes(token)) throw new Error(`AgentSetupController missing token: ${token}`);
}

const service = read('../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java');
for (const token of [
  'submitEnrollment',
  'approveEnrollment',
  'upsertRuntimeResource',
  'upsertRuntimeBinding',
  'upsertSupplyProfile',
  'RUNTIME_CONNECTED',
  'credentialToken is required when autoApprove=true',
  'Automatic Capability Catalog creation was removed',
  'Automatic Dispatch Rule creation was removed',
  'requireTenant(body.getTenantId())',
]) {
  if (!service.includes(token)) throw new Error(`AgentSetupService missing token: ${token}`);
}
for (const forbidden of ['defaultCapabilitiesForPurpose', 'defaultTaskTypesForPurpose', 'upsertCapability(', 'upsertDispatchPolicy(']) {
  if (service.includes(forbidden)) throw new Error(`AgentSetupService retains implicit setup behavior: ${forbidden}`);
}

const controllerTest = read('../ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/AgentSetupControllerMockMvcTest.java');
for (const token of ['shouldWrapFirstAgentSetupResponseInStandardEnvelope', 'shouldReturnBadRequestEnvelopeForInvalidSetupRequest', 'POST', '/admin/agents/setup', 'BAD_REQUEST']) {
  if (!controllerTest.includes(token)) throw new Error(`AgentSetupControllerMockMvcTest missing token: ${token}`);
}

const serviceTest = read('../ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/setup/AgentSetupServiceTest.java');
for (const token of [
  'shouldCreateApprovedAgentWithoutImplicitCapabilityOrDispatchData',
  'shouldCreateEnrollmentDraftOnlyWhenAutoApproveIsDisabled',
  'shouldRejectAutoApproveWithoutCredentialToken',
  'shouldPreserveExplicitCapabilityAndTaskMetadataWithoutAutoCreatingGovernance',
  'shouldRejectImplicitCapabilityCatalogCreation',
  'shouldRejectImplicitDispatchRuleCreation',
  'shouldPropagateDuplicateOrRuntimeBindingFailuresForOperatorVisibility',
]) {
  if (!serviceTest.includes(token)) throw new Error(`AgentSetupServiceTest missing token: ${token}`);
}

const adminTest = read('tests/agent-setup-backend-contract.test.ts');
for (const token of ['coreAdminApi.setupAgent', '/core-api/admin/agents/setup', 'BAD_REQUEST', 'AgentOnboardingPanel.tsx']) {
  if (!adminTest.includes(token)) throw new Error(`Admin UI agent setup backend-contract test missing token: ${token}`);
}

const onboarding = read('components/agents/AgentOnboardingPanel.tsx');
if (!onboarding.includes('coreAdminApi.setupAgent')) throw new Error('AgentOnboardingPanel must call coreAdminApi.setupAgent.');
for (const forbidden of ["tenantId: 'tenant-a'", 'createDefaultCapabilities: true', 'createDefaultDispatchRule: true']) {
  if (onboarding.includes(forbidden)) throw new Error(`AgentOnboardingPanel retains implicit setup default: ${forbidden}`);
}
for (const forbidden of ['coreAdminApi.createAgentEnrollment', 'coreAdminApi.approveAgentEnrollment']) {
  if (onboarding.includes(forbidden)) throw new Error(`AgentOnboardingPanel must not call low-level API: ${forbidden}`);
}

const smoke = read('../scripts/acceptance/agent-setup-backend-contract-smoke.mjs');
for (const token of ['POST', '/admin/agents/setup', 'RUNTIME_CONNECTED', 'read created Agent profile', '--dry-run']) {
  if (!smoke.includes(token)) throw new Error(`Stage 9 acceptance smoke script missing token: ${token}`);
}

const postman = JSON.parse(read('../docs/postman/OpenDispatch_First_Agent_Setup.postman_collection.json'));
if (postman.info?.name !== 'OpenDispatch - First Agent Setup') throw new Error('Postman collection name mismatch.');
const postmanText = JSON.stringify(postman);
for (const token of ['/admin/agents/setup', 'Missing Token Failure Case', 'readiness checks are present']) {
  if (!postmanText.includes(token)) throw new Error(`Postman collection missing token: ${token}`);
}

const pkg = JSON.parse(read('package.json'));
if (!pkg.scripts?.['verify:stage9-agent-setup']) throw new Error('package.json missing verify:stage9-agent-setup script.');

console.log('OK Stage 9 first-Agent setup backend contract tests, acceptance smoke, and Postman collection are wired.');
