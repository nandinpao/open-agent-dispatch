import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import fs from 'node:fs';
import path from 'node:path';
import { validateTaskActionDialogInput } from '../components/tasks/TaskActionDialog';

const ROOT = path.resolve(process.cwd());

function read(relativePath: string): string {
  return fs.readFileSync(path.join(ROOT, relativePath), 'utf8');
}

describe('Stage 6 governed Task action dialog', () => {
  it('requires an auditable reason', () => {
    assert.equal(
      validateTaskActionDialogInput({
        reason: 'too short',
        reasonRequired: true,
        minimumReasonLength: 12,
        confirmationPhrase: '',
      }),
      '操作原因至少需要 12 個字元。',
    );
  });

  it('requires the exact high-risk confirmation phrase', () => {
    assert.equal(
      validateTaskActionDialogInput({
        reason: '已檢查 Flow、Agent 與 Task timeline',
        reasonRequired: true,
        minimumReasonLength: 12,
        requiredPhrase: 'CONFIRM_DEAD_LETTER',
        confirmationPhrase: 'wrong',
      }),
      '請輸入確認字串：CONFIRM_DEAD_LETTER',
    );
  });

  it('accepts a complete governed action request', () => {
    assert.equal(
      validateTaskActionDialogInput({
        reason: '已檢查 Flow、Agent 與 Task timeline',
        reasonRequired: true,
        minimumReasonLength: 12,
        requiredPhrase: 'CONFIRM_DEAD_LETTER',
        confirmationPhrase: 'CONFIRM_DEAD_LETTER',
      }),
      null,
    );
  });

  it('removes native browser dialogs from the standard Task workflow', () => {
    const files = [
      'components/tasks/TaskDetailView.tsx',
      'components/tasks/TaskFailureQueuePanel.tsx',
      'components/tasks/TaskTable.tsx',
    ];
    for (const file of files) {
      const source = read(file);
      assert.doesNotMatch(source, /window\.(prompt|confirm|alert)/);
      assert.match(source, /TaskActionDialog/);
    }
  });
});
