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

// Phase 9 required contracts asserted by this spec and helpers:
// agent-options / dispatch-flows/by-agent / dispatch-evidence
// Assignment created / DispatchRequest created / Agent ACK / Agent RESULT / Task completed
test.describe('Phase 9 standard Browser E2E workflow', () => {
  test('Admin can create Source System, create Dispatch Flow, verify Agent relation, send real test Event, and inspect Runtime Decision Chain', async ({ page, request }, testInfo) => {
    const data = createStage9TestData(testInfo.workerIndex);
    const monitor = installStrictApiMonitor(page, data.tenantId);

    await authenticateAdmin(page);
    await selectTenantIfAvailable(page, data.tenantId);

    await test.step('precondition: at least one selectable Agent exists for this tenant', async () => {
      const agent = await requireSelectableAgent(request, data.tenantId);
      expect(agent.agentId).toBeTruthy();
    });

    await test.step('create Source System through standard UI CRUD page', async () => {
      await page.goto('/source-systems');
      await expect(page.getByRole('heading', { name: '來源系統' })).toBeVisible();
      await page.getByRole('button', { name: '新增來源系統' }).click();
      await page.getByLabel(/來源識別碼/).fill(data.sourceSystemId);
      await page.getByLabel(/顯示名稱/).fill(data.sourceDisplayName);
      await page.getByLabel(/說明/).fill('Phase 9 Browser E2E source master data. No dispatch authority is stored here.');
      await page.getByRole('button', { name: '儲存來源' }).click();
      await expect(page.getByText(data.sourceSystemId)).toBeVisible();

      const sources = await getJson<Array<{ sourceSystemId: string; status?: string }>>(request, `/api/admin/source-systems?tenantId=${encodeURIComponent(data.tenantId)}`);
      expect(sources.some((source) => source.sourceSystemId === data.sourceSystemId && String(source.status ?? '').toUpperCase() === 'ACTIVE')).toBeTruthy();
    });

    let selectedAgentId = '';

    await test.step('create active Dispatch Flow through UI and select backend-provided Agent option', async () => {
      const agent = await requireSelectableAgent(request, data.tenantId);
      selectedAgentId = agent.agentId;

      await page.goto('/dispatch-flows');
      await expect(page.getByRole('heading', { name: '派工流程' })).toBeVisible();
      await page.getByRole('button', { name: '建立派工流程' }).click();
      await page.getByLabel(/流程名稱/).fill(data.flowName);
      await page.getByLabel('狀態').selectOption('ACTIVE');
      await page.getByLabel(/來源系統/).selectOption(data.sourceSystemId);
      await page.getByLabel(/物件類型/).fill(data.objectType);
      await page.getByLabel(/事件類型/).fill(data.eventType);

      const agentCheckbox = page.locator('label', { hasText: selectedAgentId }).locator('input[type="checkbox"]');
      await expect(agentCheckbox, `Agent ${selectedAgentId} must be selectable in Dispatch Flow editor`).toBeEnabled();
      await agentCheckbox.check();

      // Do not select Required Capability: this verifies Capability Requirement Mode = NONE for ordinary work.
      await page.getByRole('button', { name: 'Save Dispatch Flow' }).click();
      await expect(page.getByText(data.flowName)).toBeVisible();
      await expect(page.getByText(/已儲存/)).toBeVisible();

      const flows = await waitForApiCondition<Array<{ flowId: string; flowName?: string; status?: string; sourceSystem?: string; agents?: Array<{ agentId: string }> }>>(
        request,
        `/api/admin/dispatch-flows?tenantId=${encodeURIComponent(data.tenantId)}`,
        (payload) => payload.some((flow) => flow.flowName === data.flowName && flow.sourceSystem === data.sourceSystemId),
        'new Dispatch Flow to be listed after save',
      );
      const createdFlow = flows.find((flow) => flow.flowName === data.flowName);
      expect(createdFlow?.status?.toUpperCase()).toMatch(/ACTIVE|ENABLED/);
      expect(createdFlow?.agents?.some((agent) => agent.agentId === selectedAgentId)).toBeTruthy();
    });

    await test.step('reload Flow list and verify Active count and persisted CRUD state', async () => {
      await page.reload();
      await expect(page.getByText(data.flowName)).toBeVisible();
      await expect(page.getByText(data.sourceSystemId)).toBeVisible();
      await expect(page.getByText('已啟用')).toBeVisible();
    });

    await test.step('Agent Detail shows the same Dispatch Flow relation as Runtime source of truth', async () => {
      await page.goto(`/agents/${encodeURIComponent(selectedAgentId)}`);
      await expect(page.getByText(data.flowName)).toBeVisible();
      const agentFlows = await getJson<Array<{ flowName?: string; agents?: Array<{ agentId: string }> }>>(
        request,
        `/api/admin/dispatch-flows/by-agent/${encodeURIComponent(selectedAgentId)}?tenantId=${encodeURIComponent(data.tenantId)}`,
      );
      expect(agentFlows.some((flow) => flow.flowName === data.flowName)).toBeTruthy();
    });

    let taskId = '';

    await test.step('send real test Event from Dispatch Flow and require normal Runtime route evidence', async () => {
      await page.goto('/dispatch-flows');
      await page.getByText(data.flowName).click();
      await page.getByRole('button', { name: '發送真實測試事件' }).click();
      const taskLink = page.getByRole('link', { name: /Task|正式 Task|查看 Task|前往 Task/i }).first();
      await expect(page.getByText(/正式 Task|測試事件已進入正式 Event Intake/)).toBeVisible({ timeout: 30_000 });

      const bodyText = await page.locator('body').innerText();
      const match = bodyText.match(/task[-_a-zA-Z0-9]+/i);
      expect(match, `Task id must be visible in real test-event result. Body: ${bodyText.slice(0, 1000)}`).not.toBeNull();
      taskId = match![0];

      if (await taskLink.count()) {
        await taskLink.click();
      } else {
        await page.goto(`/tasks/${encodeURIComponent(taskId)}`);
      }

      await expect(page.getByText(/Runtime Decision Chain|Event accepted|Task created|Flow matched/)).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Assignment created|DispatchRequest created|Netty delivered|Agent ACK|Agent RESULT|Task completed/i)).toBeVisible({ timeout: 60_000 });

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
          return serialized.includes('ASSIGNMENT') && serialized.includes('DISPATCH') && serialized.includes('ACK') && serialized.includes('RESULT') && serialized.includes('COMPLETED');
        },
        'Task evidence to contain Assignment, DispatchRequest, ACK, RESULT, and Completed runtime steps',
        60_000,
      );
      expect(evidence.primaryBlockerCode ?? '').not.toMatch(/SERVICE_SCOPE|ASSIGNMENT_PROFILE|SOURCE_COVERAGE|TASK_SCOPE|OPERATION_PROFILE|QUALIFICATION/);
    });

    await monitor.assertClean();
    expect(monitor.standardRequests().length).toBeGreaterThan(0);
  });
});
