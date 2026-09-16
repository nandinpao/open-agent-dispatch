import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { mergeIssueTrackingIntoTask, mergeIssueTrackingIntoTasks } from '../lib/tasks/issueTrackingBridge';
import type { CoreAdapterAction, CoreTaskRuntimeView } from '../lib/types/core';

function task(): CoreTaskRuntimeView {
  return {
    taskId: 'task-issue-ui',
    incidentId: 'incident-issue-ui',
    status: 'COMPLETED',
    payload: { existing: true }
  };
}

function action(overrides: Partial<CoreAdapterAction>): CoreAdapterAction {
  return {
    actionId: 'act-issue-ui',
    adapterType: 'ISSUE_TRACKING',
    actionType: 'ISSUE_CREATE',
    status: 'COMPLETED',
    createdAt: '2026-07-01T01:00:00.000Z',
    updatedAt: '2026-07-01T01:01:00.000Z',
    completedAt: '2026-07-01T01:02:00.000Z',
    payload: {},
    ...overrides
  };
}

describe('issue tracking bridge', () => {
  it('hydrates completed issue sync from JSON responseRef without replacing existing payload', () => {
    const merged = mergeIssueTrackingIntoTask(task(), [action({
      responseRef: JSON.stringify({
        vendor: 'GITLAB',
        issueId: '42',
        issueUrl: 'https://gitlab.example.com/group/project/-/issues/42',
        issueStatus: 'opened'
      }),
      payload: {
        agentResult: { summary: 'ERP purchase order reviewed.', formatVersion: 'agent-result.v1' },
        issueCommentMode: 'APPEND',
        issueComment: 'Agent history comment'
      }
    })]);

    const payload = merged.payload as Record<string, unknown>;
    const issueTracking = payload.issueTracking as Record<string, unknown>;
    assert.equal(payload.existing, true);
    assert.equal(issueTracking.issueVendor, 'GITLAB');
    assert.equal(issueTracking.issueId, '42');
    assert.equal(issueTracking.issueUrl, 'https://gitlab.example.com/group/project/-/issues/42');
    assert.equal(issueTracking.issueStatus, 'opened');
    assert.equal(issueTracking.syncStatus, 'SYNCED');
    assert.equal(issueTracking.lastSyncedAt, '2026-07-01T01:02:00.000Z');
    assert.equal(issueTracking.latestAgentSummary, 'ERP purchase order reviewed.');
    assert.equal(issueTracking.issueCommentMode, 'APPEND');
  });

  it('normalizes legacy simple responseRef values such as gitlab-note:42', () => {
    const merged = mergeIssueTrackingIntoTask(task(), [action({
      actionType: 'ISSUE_UPDATE_COMMENT',
      responseRef: 'gitlab-note:42',
      payload: {
        issueComment: 'Agent note body',
        agentSummary: 'Agent wrote GitLab note.'
      }
    })]);

    const issueTracking = (merged.payload as Record<string, unknown>).issueTracking as Record<string, unknown>;
    assert.equal(issueTracking.issueVendor, 'GITLAB');
    assert.equal(issueTracking.issueId, '42');
    assert.equal(issueTracking.issueActionType, 'ISSUE_UPDATE_COMMENT');
    assert.equal(issueTracking.latestAgentSummary, 'Agent wrote GitLab note.');
  });

  it('extracts vendor and issue id from linkedIssueId while sync is still pending', () => {
    const merged = mergeIssueTrackingIntoTask(task(), [action({
      actionId: 'act-pending-redmine',
      status: 'RETRY_WAITING',
      actionType: 'ISSUE_UPDATE_COMMENT',
      responseRef: undefined,
      payload: {
        linkedIssueId: 'REDMINE:701',
        issueCommentMode: 'APPEND',
        callbackMessage: 'Waiting to append agent result.'
      }
    })]);

    const issueTracking = (merged.payload as Record<string, unknown>).issueTracking as Record<string, unknown>;
    assert.equal(issueTracking.issueVendor, 'REDMINE');
    assert.equal(issueTracking.issueId, '701');
    assert.equal(issueTracking.syncStatus, 'SYNC_PENDING');
    assert.equal(issueTracking.issueRetryable, true);
    assert.equal(issueTracking.message, 'Issue Tracking action is waiting for adapter execution.');
  });

  it('surfaces failed issue sync errors and keeps the action retryable in the UI', () => {
    const merged = mergeIssueTrackingIntoTask(task(), [action({
      status: 'FAILED',
      lastError: 'Redmine create issue returned 401: unauthorized',
      payload: { vendor: 'REDMINE' }
    })]);

    const issueTracking = (merged.payload as Record<string, unknown>).issueTracking as Record<string, unknown>;
    assert.equal(issueTracking.issueVendor, 'REDMINE');
    assert.equal(issueTracking.syncStatus, 'FAILED');
    assert.equal(issueTracking.issueRetryable, true);
    assert.match(String(issueTracking.syncError), /401/);
    assert.match(String(issueTracking.message), /sync failed/);
  });

  it('hydrates task list issue links only from adapter actions matching each task id', () => {
    const first = task();
    const second: CoreTaskRuntimeView = { ...task(), taskId: 'task-without-redmine' };
    const [mergedFirst, mergedSecond] = mergeIssueTrackingIntoTasks([first, second], [
      action({
        actionId: 'act-redmine-86',
        taskId: first.taskId,
        responseRef: JSON.stringify({
          vendor: 'REDMINE',
          issueId: '86',
          issueUrl: 'http://baofire.com:8700/issues/86',
          issueStatus: '需求'
        })
      }),
      action({
        actionId: 'act-other-task',
        taskId: 'task-other',
        responseRef: JSON.stringify({
          vendor: 'REDMINE',
          issueId: '87',
          issueUrl: 'http://baofire.com:8700/issues/87'
        })
      })
    ]);

    const firstIssue = (mergedFirst.payload as Record<string, unknown>).issueTracking as Record<string, unknown>;
    assert.equal(firstIssue.issueVendor, 'REDMINE');
    assert.equal(firstIssue.issueId, '86');
    assert.equal(firstIssue.issueUrl, 'http://baofire.com:8700/issues/86');
    assert.equal((mergedSecond.payload as Record<string, unknown>).issueTracking, undefined);
  });

});
