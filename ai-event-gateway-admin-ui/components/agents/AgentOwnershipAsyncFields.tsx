'use client';

import { useCallback, useEffect, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { AsyncSearchSelectField, type SelectOption } from '@/components/access-management/shared/beginnerUi';

export interface AgentOwnershipValues {
  ownerDepartmentId: string;
  ownerGroupId: string;
  businessOwnerUserId: string;
  technicalStewardUserId: string;
  responsibilityRoleId: string;
}

export function AgentOwnershipAsyncFields({
  tenantId,
  idPrefix,
  values,
  onChange,
  onBusinessOwnerEligibilityChange,
  disabled = false,
}: Readonly<{
  tenantId: string;
  idPrefix: string;
  values: AgentOwnershipValues;
  onChange: (patch: Partial<AgentOwnershipValues>) => void;
  onBusinessOwnerEligibilityChange?: (eligible: boolean) => void;
  disabled?: boolean;
}>) {
  const [departmentOption, setDepartmentOption] = useState<SelectOption | null>(null);
  const [groupOption, setGroupOption] = useState<SelectOption | null>(null);
  const [businessOwnerOption, setBusinessOwnerOption] = useState<SelectOption | null>(null);
  const [technicalStewardOption, setTechnicalStewardOption] = useState<SelectOption | null>(null);
  const [responsibilityOption, setResponsibilityOption] = useState<SelectOption | null>(null);
  const [businessOwnerEligible, setBusinessOwnerEligible] = useState(!values.businessOwnerUserId);

  const reportEligibility = useCallback((eligible: boolean) => {
    setBusinessOwnerEligible(eligible);
    onBusinessOwnerEligibilityChange?.(eligible);
  }, [onBusinessOwnerEligibilityChange]);

  useEffect(() => {
    setDepartmentOption(null);
    setGroupOption(null);
    setBusinessOwnerOption(null);
    setTechnicalStewardOption(null);
    setResponsibilityOption(null);
    reportEligibility(true);
  }, [tenantId, reportEligibility]); // Tenant switch invalidates every cached entity label; exact IDs re-hydrate in the effects below.

  useEffect(() => {
    if (!tenantId || !values.ownerDepartmentId) { setDepartmentOption(null); return; }
    let cancelled = false;
    void accessManagementApi.department(tenantId, values.ownerDepartmentId)
      .then((item) => { if (!cancelled) setDepartmentOption({ value: item.departmentId, label: item.name, description: item.code }); })
      .catch(() => { if (!cancelled) setDepartmentOption({ value: values.ownerDepartmentId, label: values.ownerDepartmentId }); });
    return () => { cancelled = true; };
  }, [tenantId, values.ownerDepartmentId]);

  useEffect(() => {
    if (!tenantId || !values.ownerGroupId) { setGroupOption(null); return; }
    let cancelled = false;
    void accessManagementApi.group(tenantId, values.ownerGroupId)
      .then((item) => { if (!cancelled) setGroupOption({ value: item.groupId, label: item.name, description: item.code }); })
      .catch(() => { if (!cancelled) setGroupOption({ value: values.ownerGroupId, label: values.ownerGroupId }); });
    return () => { cancelled = true; };
  }, [tenantId, values.ownerGroupId]);

  useEffect(() => {
    if (!tenantId || !values.technicalStewardUserId) { setTechnicalStewardOption(null); return; }
    let cancelled = false;
    void accessManagementApi.tenantUser(tenantId, values.technicalStewardUserId)
      .then((item) => { if (!cancelled) setTechnicalStewardOption({ value: item.userId, label: item.displayName, description: item.email || item.username }); })
      .catch(() => { if (!cancelled) setTechnicalStewardOption({ value: values.technicalStewardUserId, label: values.technicalStewardUserId }); });
    return () => { cancelled = true; };
  }, [tenantId, values.technicalStewardUserId]);

  useEffect(() => {
    if (!tenantId || !values.businessOwnerUserId) {
      setBusinessOwnerOption(null);
      reportEligibility(true);
      return;
    }
    let cancelled = false;
    async function hydrateAndValidate() {
      try {
        const user = await accessManagementApi.tenantUser(tenantId, values.businessOwnerUserId);
        if (cancelled) return;
        setBusinessOwnerOption({ value: user.userId, label: user.displayName, description: user.email || user.username });
        if (!values.ownerDepartmentId) { reportEligibility(false); return; }
        let cursor = '';
        let eligible = false;
        // Exact owner validation is paged so a Person with >100 memberships is still handled correctly.
        for (let page = 0; page < 1000; page += 1) {
          const memberships = await accessManagementApi.memberships(tenantId, values.businessOwnerUserId, 100, cursor);
          if (memberships.items.some((m) => m.membershipType === 'DEPARTMENT' && m.resourceId === values.ownerDepartmentId && m.status === 'ACTIVE')) { eligible = true; break; }
          if (!memberships.nextCursor) break;
          cursor = memberships.nextCursor;
        }
        if (!cancelled) reportEligibility(eligible);
      } catch {
        if (!cancelled) {
          setBusinessOwnerOption({ value: values.businessOwnerUserId, label: values.businessOwnerUserId, description: 'Unable to verify active Department membership' });
          reportEligibility(false);
        }
      }
    }
    void hydrateAndValidate();
    return () => { cancelled = true; };
  }, [reportEligibility, tenantId, values.businessOwnerUserId, values.ownerDepartmentId]);

  const loadDepartments = useCallback(async (query: string, cursor?: string) => {
    if (!tenantId) return { options: [] };
    const pageNo = Number(cursor || '0');
    const page = await accessManagementApi.departments(tenantId, pageNo, 25, query, 'ACTIVE');
    return { options: page.items.map((item) => ({ value: item.departmentId, label: item.name, description: item.code })), nextCursor: page.hasMore ? String(pageNo + 1) : undefined };
  }, [tenantId]);

  const loadGroups = useCallback(async (query: string, cursor?: string) => {
    if (!tenantId) return { options: [] };
    const pageNo = Number(cursor || '0');
    const page = await accessManagementApi.groups(tenantId, pageNo, 25, query, '', 'ACTIVE');
    return { options: page.items.map((item) => ({ value: item.groupId, label: item.name, description: item.code })), nextCursor: page.hasMore ? String(pageNo + 1) : undefined };
  }, [tenantId]);

  const loadBusinessOwners = useCallback(async (query: string, cursor?: string) => {
    if (!tenantId || !values.ownerDepartmentId) return { options: [] };
    const page = await accessManagementApi.tenantUsers(tenantId, 25, cursor || '', query, 'ACTIVE', 'ACTIVE', '', values.ownerDepartmentId);
    return { options: page.items.map((item) => ({ value: item.userId, label: item.displayName, description: item.email || item.username })), nextCursor: page.nextCursor || undefined };
  }, [tenantId, values.ownerDepartmentId]);

  const loadTechnicalStewards = useCallback(async (query: string, cursor?: string) => {
    if (!tenantId) return { options: [] };
    const page = await accessManagementApi.tenantUsers(tenantId, 25, cursor || '', query, 'ACTIVE', 'ACTIVE');
    return { options: page.items.map((item) => ({ value: item.userId, label: item.displayName, description: item.email || item.username })), nextCursor: page.nextCursor || undefined };
  }, [tenantId]);

  const loadResponsibilities = useCallback(async (query: string, cursor?: string) => {
    if (!tenantId) return { options: [] };
    const pageNo = Number(cursor || '0');
    const page = await accessManagementApi.responsibilityTemplates(tenantId, pageNo, 25, query, 'ACTIVE');
    const eligible = page.items.filter((item) => item.allowedPrincipalTypes?.includes('AGENT'));
    return { options: eligible.map((item) => ({ value: item.roleId, label: item.roleName, description: item.description || item.riskLevel })), nextCursor: page.hasMore ? String(pageNo + 1) : undefined };
  }, [tenantId]);

  return <>
    <label className="text-sm font-semibold text-slate-700">Owner Department<div className="mt-1"><AsyncSearchSelectField id={`${idPrefix}-department`} value={values.ownerDepartmentId} onChange={(value, option) => { setDepartmentOption(option ?? null); setBusinessOwnerOption(null); reportEligibility(!values.businessOwnerUserId); onChange({ ownerDepartmentId: value, businessOwnerUserId: '' }); }} loadOptions={loadDepartments} selectedOption={departmentOption} placeholder="Search owner Department" required disabled={disabled}/></div></label>
    <label className="text-sm font-semibold text-slate-700">Owner Group<div className="mt-1"><AsyncSearchSelectField id={`${idPrefix}-group`} value={values.ownerGroupId} onChange={(value, option) => { setGroupOption(option ?? null); onChange({ ownerGroupId: value }); }} loadOptions={loadGroups} selectedOption={groupOption} placeholder="Search optional Group" disabled={disabled}/></div></label>
    <label className="text-sm font-semibold text-slate-700">Business Owner<div className="mt-1"><AsyncSearchSelectField id={`${idPrefix}-owner`} value={values.businessOwnerUserId} onChange={(value, option) => { setBusinessOwnerOption(option ?? null); reportEligibility(!value || Boolean(option)); onChange({ businessOwnerUserId: value }); }} loadOptions={loadBusinessOwners} selectedOption={businessOwnerOption} disabled={disabled || !values.ownerDepartmentId} placeholder={values.ownerDepartmentId ? 'Search active Department members' : 'Select Owner Department first'} required/></div><p className={`mt-1 text-xs font-normal ${businessOwnerEligible ? 'text-slate-500' : 'text-rose-600'}`}>Only an active Tenant member with an active membership in the selected Department can be the accountable Business Owner.</p></label>
    <label className="text-sm font-semibold text-slate-700">Technical Steward<div className="mt-1"><AsyncSearchSelectField id={`${idPrefix}-steward`} value={values.technicalStewardUserId} onChange={(value, option) => { setTechnicalStewardOption(option ?? null); onChange({ technicalStewardUserId: value }); }} loadOptions={loadTechnicalStewards} selectedOption={technicalStewardOption} placeholder="Search optional technical steward" disabled={disabled}/></div></label>
    <label className="text-sm font-semibold text-slate-700 md:col-span-2">Agent Responsibility<div className="mt-1"><AsyncSearchSelectField id={`${idPrefix}-responsibility`} value={values.responsibilityRoleId} onChange={(value, option) => { setResponsibilityOption(option ?? null); onChange({ responsibilityRoleId: value }); }} loadOptions={loadResponsibilities} selectedOption={responsibilityOption ?? (values.responsibilityRoleId ? { value: values.responsibilityRoleId, label: values.responsibilityRoleId } : null)} placeholder="Search AGENT-eligible Responsibilities" required disabled={disabled}/></div><p className="mt-1 text-xs font-normal text-slate-500">This persists the canonical AGENT Role Binding; capability metadata remains separate.</p></label>
  </>;
}
