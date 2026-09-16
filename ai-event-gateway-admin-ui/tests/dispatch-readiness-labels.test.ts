import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { beginnerCheckLabel, beginnerSkillDescription, beginnerSkillLabel, beginnerStatusLabel, humanizedCodeDescription, humanizedCodeLabel } from '../lib/dispatch-readiness/labels';


describe('dispatch readiness beginner labels', () => {
  it('renders user-friendly labels while preserving technical codes', () => {
    assert.equal(beginnerSkillLabel('TASK_EXECUTION'), '任務執行能力');
    assert.match(beginnerSkillDescription('GENERAL_AGENT'), /Agent Pool membership/);
    assert.equal(beginnerSkillLabel('UNKNOWN_SKILL'), 'UNKNOWN_SKILL');
  });

  it('maps readiness check keys to beginner-readable wording', () => {
    assert.equal(
      beginnerCheckLabel({ key: 'GOVERNANCE_APPROVED_CAPABILITY', status: 'FAIL' }),
      'Capability 核准資訊（參考）'
    );
  });

  it('humanizes operational enum/status values for non-engineers', () => {
    assert.equal(beginnerStatusLabel('WAIT_GATEWAY_ACK'), '等待 Gateway 確認');
    assert.equal(humanizedCodeLabel('IDLE', 'status'), '空閒');
    assert.match(humanizedCodeDescription('IDLE', 'status'), /不代表它沒有收到|未回報正在執行/);
  });

  it('keeps unknown capability codes opaque instead of inferring a source domain', () => {
    assert.equal(beginnerSkillLabel('SOURCE_1001_CUSTOM_ANALYSIS'), 'SOURCE_1001_CUSTOM_ANALYSIS');
    assert.match(beginnerSkillDescription('SOURCE_1001_CUSTOM_ANALYSIS'), /尚未設定友善說明/);
  });
});
