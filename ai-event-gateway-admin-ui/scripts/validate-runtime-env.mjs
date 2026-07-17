#!/usr/bin/env node
import process from 'node:process';

const PROFILE = (process.env.ADMIN_UI_SECURITY_PROFILE || process.env.ADMIN_UI_DEPLOYMENT_PROFILE || '').toLowerCase();
const FAIL_CLOSED = ['1', 'true', 'yes', 'on'].includes((process.env.ADMIN_UI_FAIL_CLOSED || '').toLowerCase());
const productionSecurity = PROFILE === 'prod' || PROFILE === 'production' || FAIL_CLOSED;

function value(name) {
  const v = process.env[name];
  return v === undefined ? '' : String(v).trim();
}

function firstNonBlank(...names) {
  for (const name of names) {
    const v = value(name);
    if (v) return { name, value: v };
  }
  return { name: names[0], value: '' };
}

function isTruthy(name, fallback = false) {
  const v = value(name).toLowerCase();
  if (!v) return fallback;
  return ['1', 'true', 'yes', 'on'].includes(v);
}

function unsafeSecret(v) {
  const n = String(v || '').trim().toLowerCase();
  return !n
    || n === 'change-me'
    || n === 'changeme'
    || n === 'password'
    || n === 'secret'
    || n === 'admin'
    || n === 'dev-token'
    || n === 'test-token'
    || n.startsWith('<')
    || n.endsWith('>')
    || n.startsWith('replace-with')
    || n.includes('replace-with')
    || n.includes('replace_with')
    || n.includes('change_me')
    || n.endsWith('change-me');
}

function requireFalse(name, fallback = false, errors) {
  if (isTruthy(name, fallback)) errors.push(`${name} must be false in production security mode`);
}

function requireTrue(name, fallback = false, errors) {
  if (!isTruthy(name, fallback)) errors.push(`${name} must be true in production security mode`);
}

function requireSecret(names, errors) {
  const resolved = firstNonBlank(...names);
  if (unsafeSecret(resolved.value)) {
    errors.push(`${names.join(' or ')} must be configured with a non-placeholder secret`);
  }
  return resolved;
}

function requireDistinct(label, resolvedSecrets, errors) {
  const seen = new Map();
  for (const item of resolvedSecrets) {
    if (!item.value) continue;
    const prior = seen.get(item.value);
    if (prior) {
      errors.push(`${label} secrets must be role-separated; ${prior.name} and ${item.name} resolve to the same value`);
    }
    seen.set(item.value, item);
  }
}

if (!productionSecurity) {
  process.exit(0);
}

const errors = [];

requireTrue('NEXT_PUBLIC_AUTH_ENABLED', true, errors);
requireFalse('NEXT_PUBLIC_USE_MOCK', false, errors);
requireFalse('CORE_FORWARD_BROWSER_AUTHORIZATION', false, errors);

requireTrue('ADMIN_UI_COOKIE_SECURE', true, errors);
for (const obsolete of ['NEXT_PUBLIC_ADMIN_AUTH_MODE','ADMIN_UI_AUTH_MODE','NEXT_PUBLIC_WS_AUTH_MODE','NEXT_PUBLIC_WS_TOKEN_QUERY_NAME']) {
  if (value(obsolete)) errors.push(`${obsolete} is obsolete and must be removed in P4-C`);
}
if (value('NEXT_PUBLIC_AUTH_STORAGE')) {
  errors.push('NEXT_PUBLIC_AUTH_STORAGE is obsolete; authentication tokens must not be stored in browser storage');
}

const coreOperator = requireSecret(['CORE_BACKEND_OPERATOR_TOKEN', 'CORE_OPERATOR_TOKEN', 'AI_EVENT_GATEWAY_CORE_OPERATOR_TOKEN'], errors);
const recoveryOperator = requireSecret(['CORE_BACKEND_RECOVERY_OPERATOR_TOKEN', 'CORE_RECOVERY_OPERATOR_TOKEN', 'CORE_RECOVERY_OPERATOR_INTERNAL_TOKEN'], errors);
const recoveryAdmin = requireSecret(['CORE_BACKEND_RECOVERY_ADMIN_TOKEN', 'CORE_RECOVERY_ADMIN_TOKEN', 'CORE_RECOVERY_ADMIN_INTERNAL_TOKEN'], errors);
const recoveryApprover = requireSecret(['CORE_BACKEND_RECOVERY_APPROVER_TOKEN', 'CORE_RECOVERY_APPROVER_TOKEN', 'CORE_RECOVERY_APPROVER_INTERNAL_TOKEN'], errors);
requireDistinct('Core operator/recovery', [coreOperator, recoveryOperator, recoveryAdmin, recoveryApprover], errors);

const coreOrigin = value('CORE_BACKEND_ORIGIN') || value('AI_EVENT_GATEWAY_CORE_BACKEND_ORIGIN');
const nettyOrigin = value('NETTY_BACKEND_ORIGIN') || value('GATEWAY_BACKEND_ORIGIN') || value('AI_EVENT_GATEWAY_BACKEND_ORIGIN');
if (!coreOrigin) errors.push('CORE_BACKEND_ORIGIN must be set in production security mode');
if (!nettyOrigin) errors.push('NETTY_BACKEND_ORIGIN or GATEWAY_BACKEND_ORIGIN must be set in production security mode');

if (errors.length > 0) {
  console.error('Admin UI production security validation failed:');
  for (const error of errors) console.error(` - ${error}`);
  console.error('Set ADMIN_UI_SECURITY_PROFILE=local for non-production developer stacks, or provide hardened production secrets.');
  process.exit(1);
}
