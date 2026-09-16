import assert from "node:assert/strict";
import { describe, it, beforeEach, afterEach } from "node:test";
import fs from "node:fs";
import path from "node:path";
import { ApiError, setCoreTenantContext } from "../lib/api/client";
import { coreAdminApi } from "../lib/api/coreAdminApi";

const originalFetch = globalThis.fetch;
const originalCoreBaseUrl = process.env.NEXT_PUBLIC_CORE_API_BASE_URL;
const originalAuthEnabled = process.env.NEXT_PUBLIC_AUTH_ENABLED;


function assertTenantScopedUrl(actual: string, expectedPath: string) {
  const url = new URL(actual, "http://opendispatch.local");
  assert.equal(url.pathname, expectedPath);
  assert.equal(url.searchParams.get("tenantId"), "tenant-a");
}

function standardResponse(data: unknown, code = "OK", message = "Success") {
  return new Response(
    JSON.stringify({ code, message, data, timestamp: "2026-07-07T00:00:00Z" }),
    {
      status: 200,
      headers: { "Content-Type": "application/json" },
    },
  );
}

function startCommandPayload() {
  return {
    runtimeType: "Docker",
    gatewayUrl: "http://127.0.0.1:18081",
    command:
      "docker run --rm -e AGENT_ID=redmine-agent-001 opendispatch/issue-agent:local",
    dockerCommand:
      "docker run --rm -e OPENSOCKET_AGENT_ID=redmine-agent-001 opendispatch/issue-agent:local",
    localCommand: "export OPENSOCKET_AGENT_ID=redmine-agent-001\nexport ADMIN_UI_MANAGED_CAPABILITIES\n./bin/start-agent.sh",
    remoteCommand: "# Run these commands on the remote host\nexport OPENSOCKET_AGENT_ID=redmine-agent-001",
    healthCheckCommand: "curl -fsS http://127.0.0.1:18081/actuator/health",
    verifyConnectionCommand:
      "curl -fsS -X POST http://127.0.0.1:18080/internal/agents/authorize-connection",
    expectedCapabilities: ["ISSUE_CREATE"],
    capabilityEnvironmentVariable: "ADMIN_UI_MANAGED_CAPABILITIES",
    logsCommand: "docker logs -f opendispatch-redmine-agent-001",
    startupSteps: ["Verify the Gateway URL is reachable from the runtime host."],
    troubleshooting: [
      {
        code: "TOKEN_MISMATCH",
        label: "Token mismatch",
        severity: "WARN",
      },
    ],
  };
}

describe("First-Agent setup backend contract", () => {
  beforeEach(() => {
    process.env.NEXT_PUBLIC_CORE_API_BASE_URL = "/core-api";
    process.env.NEXT_PUBLIC_AUTH_ENABLED = "false";
    setCoreTenantContext("tenant-a");
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    process.env.NEXT_PUBLIC_CORE_API_BASE_URL = originalCoreBaseUrl;
    process.env.NEXT_PUBLIC_AUTH_ENABLED = originalAuthEnabled;
    setCoreTenantContext("");
  });

  it("posts the Admin UI onboarding form to the single backend setup endpoint", async () => {
    const calls: Array<{ url: string; method?: string; body?: unknown }> = [];
    globalThis.fetch = (async (
      input: RequestInfo | URL,
      init?: RequestInit,
    ) => {
      calls.push({
        url: String(input),
        method: init?.method,
        body: init?.body ? JSON.parse(String(init.body)) : undefined,
      });
      return standardResponse({
        tenantId: "tenant-a",
        agentId: "redmine-agent-001",
        setupStatus: "INCOMPLETE",
        readinessChecks: [
          {
            code: "AGENT_APPROVED",
            label: "Agent approved and enabled",
            status: "READY",
            ready: true,
          },
          {
            code: "RUNTIME_CONNECTED",
            label: "Runtime connected",
            status: "PENDING",
            ready: false,
          },
        ],
        startCommand: startCommandPayload(),
      });
    }) as typeof fetch;

    const response = await coreAdminApi.setupAgent({
      tenantId: "tenant-a",
      agentId: "redmine-agent-001",
      agentName: "Redmine Issue Agent",
      purpose: "ISSUE_TRACKING",
      runtimeType: "Docker",
      gatewayUrl: "http://127.0.0.1:18081",
      credentialToken: "local-token",
      autoApprove: true,
    });

    assert.equal(calls.length, 1);
    assertTenantScopedUrl(calls[0].url, "/core-api/admin/agents/setup");
    assert.equal(calls[0].method, "POST");
    assert.deepEqual(calls[0].body, {
      tenantId: "tenant-a",
      agentId: "redmine-agent-001",
      agentName: "Redmine Issue Agent",
      purpose: "ISSUE_TRACKING",
      runtimeType: "Docker",
      gatewayUrl: "http://127.0.0.1:18081",
      credentialToken: "local-token",
      autoApprove: true,
    });
    assert.equal(response.agentId, "redmine-agent-001");
    assert.equal(response.setupStatus, "INCOMPLETE");
    assert.equal(response.readinessChecks?.[0]?.code, "AGENT_APPROVED");
    assert.match(response.startCommand?.command ?? "", /docker run/);
    assert.match(response.startCommand?.dockerCommand ?? "", /OPENSOCKET_AGENT_ID/);
    assert.match(response.startCommand?.healthCheckCommand ?? "", /actuator\/health/);
    assert.equal(response.startCommand?.troubleshooting?.[0]?.code, "TOKEN_MISMATCH");
  });

  it("surfaces setup validation failures from the standard API envelope", async () => {
    globalThis.fetch = (async () =>
      standardResponse(
        null,
        "BAD_REQUEST",
        "credentialToken is required when autoApprove=true",
      )) as typeof fetch;

    await assert.rejects(
      () =>
        coreAdminApi.setupAgent({
          agentId: "missing-token-agent",
          agentName: "Missing Token Agent",
          autoApprove: true,
        }),
      (error: unknown) =>
        error instanceof ApiError &&
        error.code === "BAD_REQUEST" &&
        /credentialToken/.test(error.message),
    );
  });

  it("keeps the first-Agent setup UI on the backend contract instead of low-level setup APIs", () => {
    const component = fs.readFileSync(
      path.join(process.cwd(), "components/agents/AgentOnboardingPanel.tsx"),
      "utf8",
    );
    assert.match(component, /coreAdminApi\.setupAgent/);
    assert.doesNotMatch(component, /coreAdminApi\.createAgentEnrollment/);
    assert.doesNotMatch(component, /coreAdminApi\.approveAgentEnrollment/);
    assert.doesNotMatch(component, /requestAgentCapability\(/);
    assert.doesNotMatch(component, /assignAgentQualification\(/);
  });

  it("reads backend-owned setup readiness from the single readiness endpoint", async () => {
    const calls: Array<{ url: string; method?: string }> = [];
    globalThis.fetch = (async (
      input: RequestInfo | URL,
      init?: RequestInit,
    ) => {
      calls.push({ url: String(input), method: init?.method });
      return standardResponse({
        tenantId: "tenant-a",
        agentId: "redmine-agent-001",
        ready: false,
        status: "INCOMPLETE",
        blockingReasons: ["RUNTIME_CONNECTED"],
        startCommand: startCommandPayload(),
        troubleshooting: [{ code: "RUNTIME_NOT_CONNECTED", label: "Runtime not connected", severity: "WARN" }],
        checks: [
          {
            code: "AGENT_APPROVED",
            label: "Agent approved and enabled",
            status: "READY",
            ready: true,
          },
          {
            code: "RUNTIME_CONNECTED",
            label: "Runtime connected",
            status: "PENDING",
            ready: false,
            action: "Start Agent Runtime",
          },
          {
            code: "ADMIN_MANAGED_CAPABILITIES_ACTIVE",
            label: "Admin-managed capabilities active",
            status: "READY",
            ready: true,
          },
        ],
      });
    }) as typeof fetch;

    const response =
      await coreAdminApi.getAgentSetupReadiness("redmine-agent-001");

    assert.equal(calls.length, 1);
    assertTenantScopedUrl(calls[0].url, "/core-api/admin/agents/redmine-agent-001/setup-readiness");
    assert.equal(calls[0].method, "GET");
    assert.equal(response.agentId, "redmine-agent-001");
    assert.equal(response.ready, false);
    assert.deepEqual(response.blockingReasons, ["RUNTIME_CONNECTED"]);
    assert.match(response.startCommand?.verifyConnectionCommand ?? "", /authorize-connection/);
    assert.equal(response.startCommand?.capabilityEnvironmentVariable, "ADMIN_UI_MANAGED_CAPABILITIES");
    assert.equal(response.troubleshooting?.[0]?.code, "RUNTIME_NOT_CONNECTED");
  });

  it("supports the Stage 11 runtime heartbeat readiness transition contract", async () => {
    const calls: Array<{ url: string; method?: string }> = [];
    let runtimeReady = false;
    globalThis.fetch = (async (
      input: RequestInfo | URL,
      init?: RequestInit,
    ) => {
      const url = String(input);
      calls.push({ url, method: init?.method });
      if (
        url.includes("/internal/gateway-nodes/") &&
        url.includes("/heartbeat")
      ) {
        runtimeReady = true;
        return standardResponse({ agentId: "redmine-agent-001", status: "IDLE" });
      }
      if (new URL(url, "http://opendispatch.local").pathname.endsWith("/setup-readiness")) {
        return standardResponse({
          tenantId: "tenant-a",
          agentId: "redmine-agent-001",
          ready: runtimeReady,
          status: runtimeReady ? "READY" : "INCOMPLETE",
          blockingReasons: runtimeReady ? [] : ["RUNTIME_CONNECTED"],
          startCommand: startCommandPayload(),
          troubleshooting: runtimeReady ? [] : [{ code: "RUNTIME_NOT_CONNECTED", label: "Runtime not connected", severity: "WARN" }],
          checks: [
            {
              code: "AGENT_APPROVED",
              label: "Agent approved and enabled",
              status: "READY",
              ready: true,
            },
            {
              code: "RUNTIME_CONNECTED",
              label: "Runtime connected",
              status: runtimeReady ? "READY" : "PENDING",
              ready: runtimeReady,
            },
            {
              code: "ADMIN_MANAGED_CAPABILITIES_ACTIVE",
              label: "Admin-managed capabilities active",
              status: "READY",
              ready: true,
            },
          ],
        });
      }
      return standardResponse({});
    }) as typeof fetch;

    const before =
      await coreAdminApi.getAgentSetupReadiness("redmine-agent-001");
    await fetch(
      "/core-api/internal/gateway-nodes/gateway-node-stage11/agents/redmine-agent-001/heartbeat",
      { method: "POST" },
    );
    const after =
      await coreAdminApi.getAgentSetupReadiness("redmine-agent-001");

    assert.equal(before.ready, false);
    assert.deepEqual(before.blockingReasons, ["RUNTIME_CONNECTED"]);
    assert.equal(after.ready, true);
    assert.deepEqual(after.blockingReasons, []);
    assert.equal(
      calls.some(
        (call) =>
          new URL(call.url, "http://opendispatch.local").pathname ===
          "/core-api/admin/agents/redmine-agent-001/setup-readiness",
      ),
      true,
    );
    assert.equal(
      calls.some((call) =>
        call.url.includes(
          "/internal/gateway-nodes/gateway-node-stage11/agents/redmine-agent-001/heartbeat",
        ),
      ),
      true,
    );
  });

  it("keeps the runtime transition acceptance script wired to gateway connected, heartbeat, disconnected, and troubleshooting failure cases", () => {
    const script = fs.readFileSync(
      path.join(
        process.cwd(),
        "../scripts/acceptance/agent-setup-backend-contract-smoke.mjs",
      ),
      "utf8",
    );
    assert.match(script, /sendRuntimeTransition/);
    assert.match(script, /authorizeConnection/);
    assert.match(script, /CREDENTIAL_INVALID/);
    assert.match(script, /sendMismatchedRuntimeSignal/);
    assert.match(
      script,
      /\/internal\/gateway-nodes\/\$\{encodeURIComponent\(gatewayNodeId\)\}\/agents\/\$\{encodeURIComponent\(agentId\)\}\/connected/,
    );
    assert.match(
      script,
      /\/internal\/gateway-nodes\/\$\{encodeURIComponent\(gatewayNodeId\)\}\/agents\/\$\{encodeURIComponent\(agentId\)\}\/heartbeat/,
    );
    assert.match(script, /sendRuntimeDisconnect/);
    assert.match(script, /expectRuntimeReady/);
    assert.match(script, /expectRuntimePending/);
  });

  it("reads the latest runtime auth failure from the backend-owned contract", async () => {
    const calls: Array<{ url: string; method?: string }> = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ url: String(input), method: init?.method });
      return standardResponse({
        agentId: "redmine-agent-001",
        hasFailure: true,
        securityEventId: "asec-001",
        eventType: "INVALID_CREDENTIAL",
        denyReason: "CREDENTIAL_INVALID",
        reason: "CREDENTIAL_INVALID",
        summary: "The latest runtime authorization failed because the credential did not match an active Core credential.",
        gatewayNodeId: "gateway-node-stage13",
        remoteAddress: "127.0.0.1",
        occurredAt: "2026-07-07T00:00:00Z",
        securityEventLink: "/security-events?agentId=redmine-agent-001&eventId=asec-001",
        troubleshooting: [
          { code: "CREDENTIAL_INVALID", label: "Credential mismatch", severity: "ERROR" },
        ],
        repairActions: [
          { actionCode: "ROTATE_CREDENTIAL", label: "Rotate credential", actionType: "EXECUTE", endpoint: "/admin/agents/redmine-agent-001/connection-repair-actions/ROTATE_CREDENTIAL", requiresCredentialToken: true },
        ],
        metadata: { sourceOfTruth: "CORE_AGENT_SECURITY_EVENTS" },
      });
    }) as typeof fetch;

    const response = await coreAdminApi.getAgentLatestAuthFailure("redmine-agent-001");

    assert.equal(calls.length, 1);
    assertTenantScopedUrl(calls[0].url, "/core-api/admin/agents/redmine-agent-001/latest-auth-failure");
    assert.equal(calls[0].method, "GET");
    assert.equal(response.hasFailure, true);
    assert.equal(response.denyReason, "CREDENTIAL_INVALID");
    assert.equal(response.troubleshooting?.[0]?.code, "CREDENTIAL_INVALID");
    assert.equal(response.repairActions?.[0]?.actionCode, "ROTATE_CREDENTIAL");
  });

  it("reads and executes backend-owned connection repair actions", async () => {
    const calls: Array<{ url: string; method?: string; body?: string | null }> = [];
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ url: String(input), method: init?.method, body: typeof init?.body === "string" ? init.body : null });
      if (new URL(String(input), "http://opendispatch.local").pathname.endsWith("/connection-repair-actions")) {
        return standardResponse({
          agentId: "redmine-agent-001",
          hasFailure: true,
          denyReason: "CREDENTIAL_INVALID",
          actions: [
            { actionCode: "ROTATE_CREDENTIAL", label: "Rotate credential", actionType: "EXECUTE", endpoint: "/admin/agents/redmine-agent-001/connection-repair-actions/ROTATE_CREDENTIAL", requiresCredentialToken: true },
          ],
          metadata: { sourceOfTruth: "CORE_AGENT_SECURITY_EVENTS" },
        });
      }
      return standardResponse({
        agentId: "redmine-agent-001",
        actionCode: "ROTATE_CREDENTIAL",
        status: "COMPLETED",
        message: "Credential rotated",
        nextActions: [],
      });
    }) as typeof fetch;

    const actions = await coreAdminApi.getAgentConnectionRepairActions("redmine-agent-001");
    const result = await coreAdminApi.executeAgentConnectionRepairAction("redmine-agent-001", "ROTATE_CREDENTIAL", {
      credentialToken: "new-token",
      reason: "test repair",
    });

    assertTenantScopedUrl(calls[0].url, "/core-api/admin/agents/redmine-agent-001/connection-repair-actions");
    assert.equal(calls[0].method, "GET");
    assert.equal(actions.actions?.[0]?.actionCode, "ROTATE_CREDENTIAL");
    assertTenantScopedUrl(calls[1].url, "/core-api/admin/agents/redmine-agent-001/connection-repair-actions/ROTATE_CREDENTIAL");
    assert.equal(calls[1].method, "POST");
    assert.equal(result.status, "COMPLETED");
  });

  it("shows the latest auth failure panel in Agent Detail", () => {
    const component = fs.readFileSync(
      path.join(process.cwd(), "components/agents/AgentDetailProductView.tsx"),
      "utf8",
    );
    assert.match(component, /Runtime Auth Failure/);
    assert.match(component, /latestAuthFailure/);
    assert.match(component, /connectionRepairActions/);
    assert.match(component, /Repair Actions/);
    assert.match(component, /Open Security Event/);
  });

});
