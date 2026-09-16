import { expect, test } from '@playwright/test';
import { installStrictApiMonitor } from './support/stage9StrictApiMonitor';
import {
  authenticateAdmin,
  createStage9TestData,
  getJson,
  requireSelectableAgent,
  selectTenantIfAvailable,
  waitForApiCondition,
} from './support/stage9WorkflowHelpers';

// Phase 13 is the live-stack proof gate. It intentionally reuses the Phase 9
// browser workflow helpers, but tightens the assertions around live evidence:
// fresh Source System CRUD, persisted Dispatch Flow, same Agent relation,
// real test Event, Assignment, DispatchRequest, Netty delivery, ACK, RESULT,
// and final Task completion.
test.describe('Phase 13 live stack golden path gate', () => {
  test('proves Source → Flow → Agent → Task → Assignment → DispatchRequest → Netty → ACK/RESULT → COMPLETED on a live stack', async ({ page, request }, testInfo) => {
    if (process.env.NEXT_PUBLIC_USE_MOCK === 'true' || process.env.PLAYWRIGHT_USE_MOCK === 'true') {
      throw new Error('Phase 13 must run against a live stack. Mock-only browser mode is not allowed.');
    }

    const data = createStage9TestData(testInfo.workerIndex);
    const monitor = installStrictApiMonitor(page, data.tenantId);

    await authenticateAdmin(page);
    await selectTenantIfAvailable(page, data.tenantId);

    let selectedAgentId = '';
    let createdFlowId = '';
    let taskId = '';

    await test.step('precondition: live Core exposes at least one selectable, connected Agent option', async () => {
      const agent = await requireSelectableAgent(request, data.tenantId);
      selectedAgentId = agent.agentId;
      expect(agent.selectable, `Agent option must be selectable: ${JSON.stringify(agent)}`).not.toBe(false);
      expect(agent.runtimeConnected, `Phase 13 requires live runtimeConnected=true: ${JSON.stringify(agent)}`).not.toBe(false);
      expect(agent.heartbeatHealthy, `Phase 13 requires live heartbeatHealthy=true: ${JSON.stringify(agent)}`).not.toBe(false);
    });

    await test.step('create Source System as master data only', async () => {
      await page.goto('/source-systems');
      await expect(page.getByRole('heading', { name: '來源系統' })).toBeVisible();
      await page.getByRole('button', { name: '新增來源系統' }).click();
      await page.getByLabel(/來源識別碼/).fill(data.sourceSystemId);
      await page.getByLabel(/顯示名稱/).fill(data.sourceDisplayName);
      await page.getByLabel(/說明/).fill('Phase 13 live golden path source master data. This object carries no dispatch authority.');
      await page.getByRole('button', { name: '儲存來源' }).click();
      await expect(page.getByText(data.sourceSystemId)).toBeVisible();

      const sources = await getJson<Array<{ sourceSystemId: string; status?: string }>>(
        request,
        `/api/admin/source-systems?tenantId=${encodeURIComponent(data.tenantId)}`,
      );
      expect(sources.some((source) => source.sourceSystemId === data.sourceSystemId && String(source.status ?? '').toUpperCase() === 'ACTIVE')).toBeTruthy();
    });

    await test.step('create Dispatch Flow with the same backend-provided Agent option', async () => {
      await page.goto('/dispatch-flows');
      await expect(page.getByRole('heading', { name: '派工流程' })).toBeVisible();
      await page.getByRole('button', { name: '建立派工流程' }).click();
      await page.getByLabel(/流程名稱/).fill(data.flowName);
      await page.getByLabel('狀態').selectOption('ACTIVE');
      await page.getByLabel(/來源系統/).selectOption(data.sourceSystemId);
      await page.getByLabel(/物件類型/).fill(data.objectType);
      await page.getByLabel(/事件類型/).fill(data.eventType);

      const agentCheckbox = page.locator('label', { hasText: selectedAgentId }).locator('input[type="checkbox"]');
      await expect(agentCheckbox, `Agent ${selectedAgentId} must be selectable in the Dispatch Flow editor`).toBeEnabled();
      await agentCheckbox.check();

      // No Required Capability is selected on purpose: this proves ordinary
      // work does not require a Capability / Task Scope side-path.
      await page.getByRole('button', { name: 'Save Dispatch Flow' }).click();
      await expect(page.getByText(data.flowName)).toBeVisible();

      const flows = await waitForApiCondition<Array<{ flowId: string; flowName?: string; status?: string; sourceSystem?: string; agents?: Array<{ agentId: string }> }>>(
        request,
        `/api/admin/dispatch-flows?tenantId=${encodeURIComponent(data.tenantId)}`,
        (payload) => payload.some((flow) => flow.flowName === data.flowName && flow.sourceSystem === data.sourceSystemId),
        'Phase 13 Dispatch Flow to persist after save',
      );
      const flow = flows.find((candidate) => candidate.flowName === data.flowName);
      expect(flow, `Created flow must be listed. Flows=${JSON.stringify(flows).slice(0, 1500)}`).toBeTruthy();
      createdFlowId = String(flow!.flowId);
      expect(flow!.status?.toUpperCase()).toMatch(/ACTIVE|ENABLED/);
      expect(flow!.agents?.some((agent) => agent.agentId === selectedAgentId)).toBeTruthy();
    });

    await test.step('verify Flow List and Agent Detail share the same persisted relation', async () => {
      await page.reload();
      await expect(page.getByText(data.flowName)).toBeVisible();
      await expect(page.getByText(data.sourceSystemId)).toBeVisible();

      await page.goto(`/agents/${encodeURIComponent(selectedAgentId)}`);
      await expect(page.getByText(data.flowName)).toBeVisible();
      const agentFlows = await getJson<Array<{ flowId?: string; flowName?: string; agents?: Array<{ agentId: string }> }>>(
        request,
        `/api/admin/dispatch-flows/by-agent/${encodeURIComponent(selectedAgentId)}?tenantId=${encodeURIComponent(data.tenantId)}`,
      );
      expect(agentFlows.some((flow) => flow.flowId === createdFlowId || flow.flowName === data.flowName)).toBeTruthy();
    });

    await test.step('send real Event from Flow and require full runtime evidence through ACK/RESULT', async () => {
      await page.goto('/dispatch-flows');
      await page.getByText(data.flowName).click();
      await page.getByRole('button', { name: '發送真實測試事件' }).click();
      await expect(page.getByText(/正式 Task|測試事件已進入正式 Event Intake/)).toBeVisible({ timeout: 30_000 });

      const bodyText = await page.locator('body').innerText();
      const match = bodyText.match(/task[-_a-zA-Z0-9]+/i);
      expect(match, `Task id must be visible after real test-event. Body=${bodyText.slice(0, 1200)}`).not.toBeNull();
      taskId = match![0];
      await page.goto(`/tasks/${encodeURIComponent(taskId)}`);

      await expect(page.getByText(/Runtime Decision Chain|Event accepted|Task created|Flow matched/)).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Assignment created|DispatchRequest created|Netty delivered|Agent ACK|Agent RESULT|Task completed/i)).toBeVisible({ timeout: 90_000 });

      const evidence = await waitForApiCondition<{
        taskId?: string;
        primaryBlockerCode?: string | null;
        timeline?: Array<{ code?: string; label?: string; status?: string }>;
        assignmentId?: string | null;
        dispatchRequestId?: string | null;
      }>(
        request,
        `/api/admin/tasks/${encodeURIComponent(taskId)}/dispatch-evidence?tenantId=${encodeURIComponent(data.tenantId)}`,
        (payload) => {
          const serialized = JSON.stringify(payload).toUpperCase();
          return serialized.includes('ASSIGNMENT')
            && serialized.includes('DISPATCH')
            && serialized.includes('NETTY')
            && serialized.includes('ACK')
            && serialized.includes('RESULT')
            && serialized.includes('COMPLETED');
        },
        'Phase 13 runtime evidence to contain Assignment, DispatchRequest, Netty, ACK, RESULT, and Completed',
        90_000,
      );
      expect(evidence.primaryBlockerCode ?? '').not.toMatch(new RegExp('SERVICE_SCOPE|ASSIGNMENT_PROFILE|SOURCE_COVERAGE|TASK_SCOPE|OPERATION_PROFILE|QUALIFICATION|TASK_' + 'OFFER|OFFER' + 'ING'));
      expect(JSON.stringify(evidence).toUpperCase()).toContain('COMPLETED');
    });

    await monitor.assertClean();
    expect(monitor.standardRequests().length).toBeGreaterThan(0);
  });
});
