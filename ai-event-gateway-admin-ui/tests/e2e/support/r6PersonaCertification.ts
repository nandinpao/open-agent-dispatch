import { createHmac } from 'node:crypto';
import { expect, type Page } from '@playwright/test';

export type R6PersonaKey =
  | 'ROOT'
  | 'PLATFORM_ADMIN'
  | 'TENANT_A_ADMIN'
  | 'TENANT_B_ADMIN'
  | 'USER_ADMIN'
  | 'ORGANIZATION_ADMIN'
  | 'ACCESS_ADMIN'
  | 'SECURITY_ADMIN'
  | 'DISPATCH_ADMIN'
  | 'OPERATOR'
  | 'VIEWER'
  | 'IT_DEPARTMENT_ADMIN'
  | 'HR_DEPARTMENT_ADMIN'
  | 'GROUP_OPERATOR'
  | 'AUDITOR'
  | 'NORMAL_USER'
  | 'DUAL_TENANT_USER'
  | 'SUSPENDED_USER'
  | 'GROUP_INHERITED_USER'
  | 'REVOCATION_USER';

export interface R6PersonaCredential {
  key: R6PersonaKey;
  username: string;
  password: string;
  totpSecret?: string;
  recoveryCode?: string;
}

export interface R6Session {
  userId: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
  permissionScopes: Record<string, string[]>;
  selectedTenantId: string;
  tenantChoices: Array<{ tenantId: string; tenantCode?: string; tenantName?: string; membershipStatus?: string }>;
  requiredActions: string[];
}

export interface R6Entitlements {
  contractVersion: string;
  workspaceKind: 'TENANT' | 'INSTANCE_ROOT';
  tenantId: string;
  navigation: Array<{ featureId: string; route: string; label: string; displayMode: 'READ_ONLY' | 'ENABLED'; children: Array<{ featureId: string; route: string; label: string; displayMode: 'READ_ONLY' | 'ENABLED' }> }>;
  pages: Record<string, { displayMode: 'HIDDEN' | 'READ_ONLY' | 'ENABLED'; allowed: boolean; route: string; denialReason: string; surface: string }>;
  actions: string[];
  actionEntitlements: Record<string, { actionId: string; displayMode: 'HIDDEN' | 'ENABLED'; scopes: string[] }>;
  actionScopes?: Record<string, string[]>;
}

function env(name: string): string {
  return (process.env[name] ?? '').trim();
}

export function requiredEnv(name: string): string {
  const value = env(name);
  if (!value) throw new Error(`R6 certification requires ${name}. Use release/rbac-convergence-r6/r6-persona-certification.env.example as the checklist.`);
  return value;
}

export function persona(key: R6PersonaKey): R6PersonaCredential {
  return {
    key,
    username: requiredEnv(`R6_${key}_USERNAME`),
    password: requiredEnv(`R6_${key}_PASSWORD`),
    totpSecret: env(`R6_${key}_TOTP_SECRET`) || undefined,
    recoveryCode: env(`R6_${key}_RECOVERY_CODE`) || undefined,
  };
}

function decodeBase32(value: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const normalized = value.toUpperCase().replace(/[^A-Z2-7]/g, '');
  let bits = '';
  for (const character of normalized) {
    const index = alphabet.indexOf(character);
    if (index < 0) throw new Error('Invalid base32 TOTP secret.');
    bits += index.toString(2).padStart(5, '0');
  }
  const bytes: number[] = [];
  for (let offset = 0; offset + 8 <= bits.length; offset += 8) bytes.push(Number.parseInt(bits.slice(offset, offset + 8), 2));
  return Buffer.from(bytes);
}

function normalizeTotpSecret(value: string): string {
  const trimmed = value.trim();
  if (!trimmed.toLowerCase().startsWith('otpauth://')) return trimmed;
  const parsed = new URL(trimmed);
  return parsed.searchParams.get('secret') ?? '';
}

export function currentTotp(secretOrUri: string, nowMs = Date.now()): string {
  const secret = normalizeTotpSecret(secretOrUri);
  if (!secret) throw new Error('TOTP secret is empty.');
  const counter = Math.floor(nowMs / 30_000);
  const message = Buffer.alloc(8);
  let value = counter;
  for (let index = 7; index >= 0; index -= 1) {
    message[index] = value % 256;
    value = Math.floor(value / 256);
  }
  const digest = createHmac('sha1', decodeBase32(secret)).update(message).digest();
  const offset = digest[digest.length - 1] & 0x0f;
  const binary = ((digest[offset] & 0x7f) << 24)
    | ((digest[offset + 1] & 0xff) << 16)
    | ((digest[offset + 2] & 0xff) << 8)
    | (digest[offset + 3] & 0xff);
  return String(binary % 1_000_000).padStart(6, '0');
}

/**
 * R6 intentionally never selects a Tenant during login. The backend resolves the
 * administrator-configured home Tenant from canonical Tenant Membership.
 */
export async function loginPersona(page: Page, credential: R6PersonaCredential, expectedTenantId?: string): Promise<void> {
  await page.goto('/login');
  await expect(page.getByLabel('Tenant')).toHaveCount(0);
  await page.getByLabel('Username').fill(credential.username);
  await page.getByLabel('Password').fill(credential.password);
  await page.getByRole('button', { name: /^Sign in$/ }).click();

  for (let step = 0; step < 4; step += 1) {
    if (page.url().includes('/mfa-enrollment')) {
      throw new Error(`${credential.key} is not certification-ready: MFA enrollment is still required.`);
    }
    const authenticator = page.getByLabel('Authenticator code');
    if (await authenticator.isVisible().catch(() => false)) {
      if (credential.recoveryCode) {
        await page.getByLabel('Use a one-time recovery code').check();
        await page.getByLabel('Recovery code').fill(credential.recoveryCode);
      } else if (credential.totpSecret) {
        await authenticator.fill(currentTotp(credential.totpSecret));
      } else {
        throw new Error(`${credential.key} requires MFA. Provide R6_${credential.key}_TOTP_SECRET or R6_${credential.key}_RECOVERY_CODE.`);
      }
      await page.getByRole('button', { name: 'Verify and continue' }).click();
      continue;
    }
    if (!page.url().includes('/login')) break;
    await page.waitForTimeout(250);
  }

  await expect(page, `${credential.key} must complete a real browser sign-in`).not.toHaveURL(/\/login(?:\?|$)/);
  if (expectedTenantId) {
    expect((await session(page)).selectedTenantId, `${credential.key} must enter the administrator-configured home workspace`).toBe(expectedTenantId);
  }
}

export async function expectLoginDenied(page: Page, credential: R6PersonaCredential): Promise<void> {
  await page.goto('/login');
  await expect(page.getByLabel('Tenant')).toHaveCount(0);
  await page.getByLabel('Username').fill(credential.username);
  await page.getByLabel('Password').fill(credential.password);
  await page.getByRole('button', { name: /^Sign in$/ }).click();
  await expect(page).toHaveURL(/\/login(?:\?|$)/);
  await expect(page.getByRole('alert')).toBeVisible();
}

export async function session(page: Page): Promise<R6Session> {
  const response = await page.request.get('/api/session');
  expect(response.status(), 'canonical /api/session must be readable after sign-in').toBe(200);
  return await response.json() as R6Session;
}

export async function entitlements(page: Page): Promise<R6Entitlements> {
  const response = await page.request.get('/api/ui/entitlements');
  expect(response.status(), 'backend-owned UI entitlements must be readable after sign-in').toBe(200);
  return await response.json() as R6Entitlements;
}

export function expectFeatureMatrix(value: R6Entitlements, allowed: string[], denied: string[]): void {
  const navigation = new Set(value.navigation.map((item) => item.featureId));
  for (const feature of allowed) {
    expect(value.pages[feature]?.displayMode, `${feature} page must be visible`).not.toBe('HIDDEN');
    if (value.navigation.some((item) => item.featureId === feature) || ['access-people', 'access-organization', 'access-governance', 'access-security'].includes(feature)) continue;
    expect(navigation.has(feature), `${feature} must be present in navigation`).toBe(true);
  }
  for (const feature of denied) expect(value.pages[feature]?.displayMode, `${feature} page must be hidden`).toBe('HIDDEN');
}

export function expectActionModes(value: R6Entitlements, enabled: string[], hidden: string[]): void {
  for (const action of enabled) expect(value.actionEntitlements[action]?.displayMode, `${action} must be enabled`).toBe('ENABLED');
  for (const action of hidden) expect(value.actionEntitlements[action]?.displayMode, `${action} must be hidden`).toBe('HIDDEN');
}

export async function expectDirectPageDenied(page: Page, route: string): Promise<void> {
  await page.goto(route);
  await expect(page.getByRole('heading', { name: 'This page is not available in your current workspace' })).toBeVisible();
}

export async function expectNoInteractiveTenantSwitch(page: Page): Promise<void> {
  await expect(page.getByLabel('Active Tenant')).toHaveCount(0);
  await expect(page.getByLabel('Current workspace')).toBeVisible();
}

export async function getCoreJson<T>(page: Page, path: string): Promise<{ status: number; body?: T }> {
  const response = await page.request.get(`/core-api${path}`);
  if (!response.ok()) return { status: response.status() };
  return { status: response.status(), body: await response.json() as T };
}
