import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildTaskWorkbenchDisplay } from '../lib/tasks/taskWorkbench';
import type { TaskDispatchDashboardRow } from '../lib/dashboard/taskDispatchMerge';

function row(overrides: Partial<TaskDispatchDashboardRow['task']> = {}): TaskDispatchDashboardRow {
  return {
    task: {
      taskId: 'task-redmine-ui',
      status: 'COMPLETED',
      taskType: 'MES_ALARM_TRIAGE',
      sourceSystem: 'MES',
      objectType: 'EQUIPMENT',
      objectId: 'EQP-POSTMAN-001',
      eventType: 'EQUIPMENT_ALARM',
      errorCode: 'TEMP_HIGH',
      payload: {
        severity: 'CRITICAL',
        issueTracking: {
          issueVendor: 'REDMINE',
          issueId: '86',
          issueUrl: 'http://baofire.com:8700/issues/86',
          syncStatus: 'SYNCED'
        }
      },
      ...overrides
    },
    source: { task: 'CORE', delivery: 'MISSING', callbackRelay: 'MISSING' }
  };
}

describe('task workbench display', () => {
  it('surfaces severity from event payload when task priority is not present', () => {
    const display = buildTaskWorkbenchDisplay(row());
    assert.equal(display.severity.code, 'CRITICAL');
    assert.equal(display.severity.isHighImpact, true);
    assert.match(display.severity.label, /立即處理/);
  });

  it('keeps Redmine issue link data available for both task list and detail pages', () => {
    const display = buildTaskWorkbenchDisplay(row());
    assert.equal(display.issueBridge.vendor, 'REDMINE');
    assert.equal(display.issueBridge.issueId, '86');
    assert.equal(display.issueBridge.issueUrl, 'http://baofire.com:8700/issues/86');
    assert.equal(display.issueBridge.status, 'LINKED');
  });

  it('does not infer a source system from equipment fields when sourceSystem is absent', () => {
    const display = buildTaskWorkbenchDisplay(row({
      sourceSystem: undefined,
      eventType: 'EQUIPMENT_ALARM',
      objectType: 'EQUIPMENT',
      plantId: 'FAB-01'
    }));

    assert.match(display.title, /未提供來源系統/);
    assert.match(display.sourceLabel, /^未提供來源系統/);
    assert.doesNotMatch(display.title, /【CRITICAL】MES/);
  });

});
