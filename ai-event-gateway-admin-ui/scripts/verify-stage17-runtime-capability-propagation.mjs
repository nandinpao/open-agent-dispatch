#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = path.basename(process.cwd()) === 'ai-event-gateway-admin-ui' ? path.resolve(process.cwd(), '..') : process.cwd();
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const fail = (message) => {
  console.error(`[stage17-runtime-capability] ${message}`);
  process.exit(1);
};
const mustInclude = (file, content, needle) => {
  if (!content.includes(needle)) fail(`${file} must include ${needle}`);
};
const mustNotInclude = (file, content, needle) => {
  if (content.includes(needle)) fail(`${file} must not include ${needle}`);
};

const clientFile = 'ai-event-gateway-netty/scripts/netty-tcp-agent-client.js';
const client = read(clientFile);
mustInclude(clientFile, client, "OPENSOCKET_AGENT_CAPABILITIES");
mustInclude(clientFile, client, "supportedCapabilities: capabilities");
mustInclude(clientFile, client, "runtimeCapabilities: capabilities");
mustInclude(clientFile, client, "capabilities,");
mustNotInclude(clientFile, client, "legacyCapabilitiesEnabled\n  ? capabilityVariants");

const clusterFile = 'ai-event-gateway-netty/scripts/cluster-run-many-agents.sh';
const cluster = read(clusterFile);
mustInclude(clusterFile, cluster, "preset_capabilities_for_agent");
mustInclude(clusterFile, cluster, "agent-cluster-node-001-002");
mustInclude(clusterFile, cluster, "MES_ALARM_TRIAGE,MES_LOT_TRACE,MES_YIELD_ANOMALY_ANALYSIS");
mustInclude(clusterFile, cluster, "OPENSOCKET_AGENT_CAPABILITIES=\${runtime_capabilities}");
mustInclude(clusterFile, cluster, "AGENT_CLUSTER_CAPABILITY_PRESETS");

const bootstrapFile = 'ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js';
const bootstrap = read(bootstrapFile);
mustInclude(bootstrapFile, bootstrap, "runtimeCapabilitiesForAgent");
mustInclude(bootstrapFile, bootstrap, "agent-cluster-node-001-002");
mustInclude(bootstrapFile, bootstrap, "MES_ALARM_TRIAGE");
mustInclude(bootstrapFile, bootstrap, "capabilityBootstrap");

const registerPayloadFile = 'ai-event-gateway-netty/gateway-model/src/main/java/com/opensocket/aievent/gateway/netty/agent/dto/AgentRegisterPayload.java';
const registerPayload = read(registerPayloadFile);
mustInclude(registerPayloadFile, registerPayload, "Map<String, Object> capabilityProfile");
mustInclude(registerPayloadFile, registerPayload, "capabilityProfile = Map.of()");

const registryFile = 'ai-event-gateway-netty/gateway-core/src/main/java/com/opensocket/aievent/gateway/netty/agent/AgentRegistry.java';
const registry = read(registryFile);
mustInclude(registryFile, registry, "initialMetadata(payload)");
mustInclude(registryFile, registry, "metadata.put(\"capabilityProfile\", payload.capabilityProfile())");


const registryTestFile = 'ai-event-gateway-netty/gateway-core/src/test/java/com/opensocket/aievent/gateway/netty/agent/AgentRegistryTest.java';
const registryTest = read(registryTestFile);
mustInclude(registryTestFile, registryTest, 'shouldPreserveRegisterCapabilityProfileAsRuntimeMetadata');
mustInclude(registryTestFile, registryTest, 'MES_LOT_TRACE');

const syncTestFile = 'ai-event-gateway-netty/gateway-core/src/test/java/com/opensocket/aievent/gateway/netty/directory/CoreDirectorySyncServiceTest.java';
const syncTest = read(syncTestFile);
mustInclude(syncTestFile, syncTest, 'capabilityProfile');
mustInclude(syncTestFile, syncTest, 'MES_ALARM_TRIAGE');

const runtimeRepoFile = 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/InMemoryAgentRuntimeStateRepository.java';
const runtimeRepo = read(runtimeRepoFile);
mustInclude(runtimeRepoFile, runtimeRepo, 'profile.get("supportedCapabilities")');
mustInclude(runtimeRepoFile, runtimeRepo, 'profile.get("runtimeCapabilities")');

const directoryRepoFile = 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/InMemoryAgentDirectoryRepository.java';
const directoryRepo = read(directoryRepoFile);
mustInclude(directoryRepoFile, directoryRepo, 'profile.get("supportedCapabilities")');
mustInclude(directoryRepoFile, directoryRepo, 'profile.get("runtimeCapabilities")');

const daoFile = 'ai-event-gateway-core/database-platform/src/main/resources/mybatis/postgresql/agent/AgentDirectoryDao.xml';
const dao = read(daoFile);
mustInclude(daoFile, dao, "'supportedCapabilities'");
mustInclude(daoFile, dao, "'runtimeCapabilities'");



const postmanFile = 'docs/postman/OpenDispatch_First_Agent_Setup.postman_collection.json';
const postman = read(postmanFile);
mustInclude(postmanFile, postman, 'supportedCapabilities');
mustInclude(postmanFile, postman, 'runtimeCapabilities');

const acceptanceFile = 'scripts/acceptance/agent-setup-backend-contract-smoke.mjs';
const acceptance = read(acceptanceFile);
mustInclude(acceptanceFile, acceptance, 'supportedCapabilities');
mustInclude(acceptanceFile, acceptance, 'runtimeCapabilities');

console.log('[stage17-runtime-capability] OK');
