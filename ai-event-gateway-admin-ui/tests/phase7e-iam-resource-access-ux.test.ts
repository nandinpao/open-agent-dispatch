import assert from 'node:assert/strict';
import test from 'node:test';
import { accessReviewDecisionExperience, explicitDenyExperience, ownershipTransferImpact, separationOfDuties, summarizePermissionBundle, validateAccessRequest, validateScopeGrantDraft } from '@/lib/phase7e/iamResourceAccessUx';

test('Role bundle summarizes administrative and critical impact', () => {
  const summary=summarizePermissionBundle([
    {permissionCode:'task.read'},
    {permissionCode:'task.update'},
    {permissionCode:'resource.deny.create'},
    {permissionCode:'permission.catalog.publish'},
  ]);
  assert.equal(summary.total,4);
  assert.equal(summary.read,1);
  assert.equal(summary.write,1);
  assert.equal(summary.critical,2);
  assert.equal(summary.requiresIndependentApproval,true);
});

test('Scope Grant validation blocks SQL, unsupported scope, and permanent critical access', () => {
  const result=validateScopeGrantDraft({permissionCode:'select * from users',resourceType:'TASK',scopeType:'RESOURCE',scopeRefId:'task-1',visibilityLevel:'FULL',validFrom:'2026-08-01T12:00:00Z',validTo:null,grantReason:'Need this access for a governed support operation.',riskLane:'CRITICAL'},new Date('2026-08-01T10:00:00Z'));
  assert.equal(result.status,'BLOCKED');
  assert.ok(result.issues.some(issue=>/SQL/i.test(issue)));
  assert.ok(result.issues.some(issue=>/expiration/i.test(issue)));
});

test('Temporary Access Request cannot select raw permission expressions or exceed 30 days', () => {
  const result=validateAccessRequest({requestedAction:'permissionCode = task.update',resourceType:'TASK',resourceId:'task-1',businessPurpose:'Temporary repair access for a documented production incident.',durationHours:721,requestedVisibility:'STANDARD'});
  assert.equal(result.status,'BLOCKED');
  assert.ok(result.issues.length>=2);
});

test('Separation of duties blocks requester self-approval', () => {
  const denied=separationOfDuties({requesterId:'user-1',actorId:'user-1',action:'APPROVE'});
  assert.equal(denied.allowed,false);
  const allowed=separationOfDuties({requesterId:'user-1',actorId:'user-2',action:'APPROVE'});
  assert.equal(allowed.allowed,true);
});

test('Ownership transfer impact and deny experience preserve backend authority', () => {
  const impact=ownershipTransferImpact({participantCount:10,activeGrantCount:8,activeDenyCount:1,childResourceCount:7,changesDepartment:true,changesGroup:true});
  assert.equal(impact.risk,'HIGH_RISK');
  assert.equal(impact.requiresApproval,true);
  const deny=explicitDenyExperience('CRITICAL','ACTIVE');
  assert.equal(deny.requiresIndependentApproval,true);
  assert.match(deny.authorityEffect,/overrides/i);
  assert.match(accessReviewDecisionExperience('REQUEST_MORE_INFORMATION').effect,/without granting/i);
});
