'use client';

import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { CommandMessage } from '@/components/common/CommandMessage';
import { IsoDateTimePicker } from '@/components/common/IsoDateTimePicker';
import { CredentialTokenInput } from '@/components/agents/CredentialTokenInput';
import { CapabilityCardSelector } from '@/components/agents/CapabilityCardSelector';
import { GovernedSelect, LegacyValueWarning } from '@/components/governance/StrictSelectionControls';
import { GOVERNED_AGENT_TYPES, GOVERNED_OWNER_TEAMS } from '@/lib/governance/strictSelection';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { JsonViewer } from '@/components/common/JsonViewer';
import { ListFilterBar, type SelectFilterConfig } from '@/components/common/ListFilterBar';
import { LoadingBox } from '@/components/common/LoadingBox';
import { PaginationControls } from '@/components/common/PaginationControls';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useAuth } from '@/components/auth/AuthProvider';
import { isRuntimeObservedEnrollment, useAgentEnrollments } from '@/hooks/useAgentEnrollments';
import type { AgentEnrollmentApprovalRequest, AgentEnrollmentRequest, CoreAgentCapabilityCatalog } from '@/lib/types/core';
import { buildDefaultApprovalRequest, parseCapabilitiesCsv, parseScopeCsv } from '@/lib/agents/enrollmentWorkflow';
import { isCorrectableEnrollmentStatus, isRejectedEnrollmentStatus, normalizeEnrollmentStatus } from '@/lib/agents/governanceStatus';
import { getReviewedTimestamp } from '@/lib/agents/agentRuntimeDisplay';
import { formatDateTime } from '@/lib/utils/format';
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from '@/lib/utils/list';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { requireCoreTenantContext } from '@/lib/api/coreClient';

const allValue = 'ALL';

type DraftById = Record<string, AgentApprovalDraft>;

interface AgentApprovalDraft {
  agentId: string;
  agentName: string;
  agentType: string;
  tenantId: string;
  ownerTeam: string;
  description: string;
  capabilitiesCsv: string;
  scopesCsv: string;
  credentialToken: string;
  credentialExpiresAt: string;
  comment: string;
}

function matchesEnrollment(enrollment: AgentEnrollmentRequest, query: string, status: string): boolean {
  if (status !== allValue && normalizeEnrollmentStatus(enrollment.status) !== normalizeEnrollmentStatus(status)) return false;
  return recordIncludesQuery([
    enrollment.enrollmentId,
    enrollment.claimedAgentId,
    enrollment.agentName,
    enrollment.agentType,
    enrollment.tenantId,
    enrollment.fingerprint,
    enrollment.remoteAddress,
    enrollment.status
  ], query);
}

function canReview(enrollment: AgentEnrollmentRequest): boolean {
  return isRuntimeObservedEnrollment(enrollment) || isCorrectableEnrollmentStatus(enrollment.status);
}

function buildDraft(enrollment: AgentEnrollmentRequest): AgentApprovalDraft {
  const defaults = buildDefaultApprovalRequest(enrollment);
  return {
    agentId: defaults.agentId ?? enrollment.claimedAgentId ?? enrollment.agentName ?? '',
    agentName: defaults.agentName ?? enrollment.agentName ?? enrollment.claimedAgentId ?? '',
    agentType: defaults.agentType ?? enrollment.agentType ?? 'UNKNOWN',
    tenantId: requireCoreTenantContext(defaults.tenantId ?? enrollment.tenantId),
    ownerTeam: defaults.ownerTeam ?? '',
    description: defaults.description ?? '',
    capabilitiesCsv: (defaults.capabilities ?? []).join(','),
    scopesCsv: (defaults.scopes ?? [{ systemCode: '*', taskType: '*', enabled: true }])
      .map((scope) => [scope.systemCode ?? '*', scope.taskType ?? '*', scope.siteCode].filter(Boolean).join('/'))
      .join(','),
    credentialToken: defaults.credentialToken ?? '',
    credentialExpiresAt: defaults.credentialExpiresAt ?? '',
    comment: defaults.comment ?? ''
  };
}

function draftToApprovalRequest(draft: AgentApprovalDraft): AgentEnrollmentApprovalRequest {
  return {
    agentId: draft.agentId.trim(),
    agentName: draft.agentName.trim() || draft.agentId.trim(),
    agentType: draft.agentType.trim() || 'UNKNOWN',
    tenantId: requireCoreTenantContext(draft.tenantId),
    ownerTeam: draft.ownerTeam.trim() || undefined,
    description: draft.description.trim() || undefined,
    capabilities: parseCapabilitiesCsv(draft.capabilitiesCsv),
    scopes: parseScopeCsv(draft.scopesCsv || '*/*', requireCoreTenantContext(draft.tenantId)),
    credentialToken: draft.credentialToken.trim() || undefined,
    credentialExpiresAt: draft.credentialExpiresAt.trim() || undefined,
    comment: draft.comment.trim() || undefined
  };
}

function DraftEditor({
  draft,
  onChange,
  onApprove,
  submitting,
  capabilityCatalog,
  loadingCapabilities
}: Readonly<{
  draft: AgentApprovalDraft;
  onChange: (draft: AgentApprovalDraft) => void;
  onApprove: () => void;
  submitting: boolean;
  capabilityCatalog: CoreAgentCapabilityCatalog[];
  loadingCapabilities: boolean;
}>) {
  const { selectedTenantId } = useAuth();

  useEffect(() => {
    if (selectedTenantId && draft.tenantId !== selectedTenantId) {
      onChange({ ...draft, tenantId: selectedTenantId });
    }
  }, [draft, onChange, selectedTenantId]);

  function setField<K extends keyof AgentApprovalDraft>(key: K, value: AgentApprovalDraft[K]) {
    onChange({ ...draft, [key]: value });
  }

  const inputClass = 'rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100';

  return (
    <div className="mt-3 rounded-2xl border border-emerald-100 bg-emerald-50/60 p-4">
      <div className="mb-3 text-sm font-semibold text-emerald-900">Draft Core Agent Profile</div>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        <label className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Agent ID
          <input className={inputClass} value={draft.agentId} onChange={(event) => setField('agentId', event.target.value)} />
        </label>
        <label className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Agent Name
          <input className={inputClass} value={draft.agentName} onChange={(event) => setField('agentName', event.target.value)} />
        </label>
        <GovernedSelect label="Agent Type" value={draft.agentType} options={GOVERNED_AGENT_TYPES} onChange={(value) => setField('agentType', value)} />
        <label className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Tenant
          <input className={`${inputClass} bg-slate-100`} value={selectedTenantId || draft.tenantId} readOnly aria-label="Workspace Tenant" />
        </label>
        <GovernedSelect label="Owner Team" value={draft.ownerTeam} options={GOVERNED_OWNER_TEAMS} onChange={(value) => setField('ownerTeam', value)} />
        <CredentialTokenInput
          className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 xl:col-span-2"
          inputClassName={inputClass}
          value={draft.credentialToken}
          onChange={(value) => setField('credentialToken', value)}
          placeholder="required before Approve / Approve Again"
          helperText="Generate Token uses browser Web Crypto. Keep the generated token and update the actual Agent startup/configuration with the same value."
        />
        <IsoDateTimePicker
          className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500"
          inputClassName={inputClass}
          value={draft.credentialExpiresAt}
          onChange={(value) => setField('credentialExpiresAt', value)}
        />
        <div className="xl:col-span-3 space-y-2">
          <LegacyValueWarning label="agent type" values={[draft.agentType]} options={GOVERNED_AGENT_TYPES} />
          <LegacyValueWarning label="owner team" values={[draft.ownerTeam]} options={GOVERNED_OWNER_TEAMS} />
          <CapabilityCardSelector
            capabilities={capabilityCatalog}
            selectedCodes={parseCapabilitiesCsv(draft.capabilitiesCsv)}
            onChange={(codes) => setField('capabilitiesCsv', codes.join(','))}
            loading={loadingCapabilities}
            title="Approved capability cards"
            description="Select governed ACTIVE capabilities from the catalog. Free-form CSV is no longer a trusted dispatch input."
          />
        </div>
        <label className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Scopes CSV
          <input className={inputClass} value={draft.scopesCsv} onChange={(event) => setField('scopesCsv', event.target.value)} placeholder="SRC_E2E_7F28/*,FACTORY_IOT_01/*" />
        </label>
        <label className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 xl:col-span-2">
          Description
          <input className={inputClass} value={draft.description} onChange={(event) => setField('description', event.target.value)} />
        </label>
        <label className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
          Review Comment
          <input className={inputClass} value={draft.comment} onChange={(event) => setField('comment', event.target.value)} />
        </label>
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button
          type="button"
          disabled={submitting || !draft.agentId.trim() || !draft.credentialToken.trim()}
          onClick={onApprove}
          className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? 'Approving...' : 'Approve Draft'}
        </button>
        <span className="text-xs text-emerald-800">先編輯 draft，再按 Approve Draft；不再連續彈出 prompt dialog。</span>
      </div>
    </div>
  );
}

export function AgentEnrollmentTable() {
  const { selectedTenantId } = useAuth();
  const searchParams = useSearchParams();
  const { data, loading, refreshing, error, lastUpdatedAt, refresh, commandMessage, createEnrollmentFromObserved, approveEnrollment, rejectEnrollment, sourceErrors } = useAgentEnrollments();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState(allValue);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draftById, setDraftById] = useState<DraftById>({});
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [capabilityCatalog, setCapabilityCatalog] = useState<CoreAgentCapabilityCatalog[]>([]);
  const [loadingCapabilities, setLoadingCapabilities] = useState(false);

  const requestedAgentId = searchParams.get('agentId');

  useEffect(() => {
    if (requestedAgentId && !search) {
      setSearch(requestedAgentId);
    }
  }, [requestedAgentId, search]);

  useEffect(() => {
    let cancelled = false;
    setLoadingCapabilities(true);
    coreAdminApi.getCapabilities('ACTIVE', undefined, selectedTenantId)
      .then((items) => {
        if (!cancelled) setCapabilityCatalog(items);
      })
      .finally(() => {
        if (!cancelled) setLoadingCapabilities(false);
      });
    return () => { cancelled = true; };
  }, [selectedTenantId]);

  const enrollments = useMemo(() => data ?? [], [data]);
  const filtered = useMemo(
    () => enrollments.filter((enrollment) => matchesEnrollment(enrollment, search, statusFilter)),
    [enrollments, search, statusFilter]
  );
  const pagination = useMemo(() => paginateItems(filtered, { page, pageSize }), [filtered, page, pageSize]);

  useEffect(() => {
    setPage(1);
  }, [search, statusFilter, pageSize]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const statuses = uniqueSortedValues(enrollments.map((enrollment) => enrollment.status));
    return [
      {
        id: 'status',
        label: 'Review Status',
        value: statusFilter,
        onChange: setStatusFilter,
        options: [{ value: allValue, label: 'All Statuses' }, ...statuses.map((status) => ({ value: status, label: status }))]
      }
    ];
  }, [enrollments, statusFilter]);

  function clearFilters() {
    setSearch('');
    setStatusFilter(allValue);
  }

  function ensureDraft(enrollment: AgentEnrollmentRequest): AgentApprovalDraft {
    const existing = draftById[enrollment.enrollmentId];
    if (existing) return existing;
    const draft = buildDraft(enrollment);
    setDraftById((current) => ({ ...current, [enrollment.enrollmentId]: draft }));
    return draft;
  }

  function openDraft(enrollment: AgentEnrollmentRequest) {
    ensureDraft(enrollment);
    setEditingId(enrollment.enrollmentId);
  }

  async function directCreateCoreEnrollment(enrollment: AgentEnrollmentRequest) {
    setSubmittingId(enrollment.enrollmentId);
    try {
      await createEnrollmentFromObserved(enrollment);
    } finally {
      setSubmittingId(null);
    }
  }

  async function approveWithDraft(enrollment: AgentEnrollmentRequest) {
    const draft = draftById[enrollment.enrollmentId] ?? buildDraft(enrollment);
    setSubmittingId(enrollment.enrollmentId);
    try {
      await approveEnrollment(enrollment, draftToApprovalRequest(draft));
      setEditingId(null);
      setDraftById((current) => {
        const next = { ...current };
        delete next[enrollment.enrollmentId];
        return next;
      });
    } finally {
      setSubmittingId(null);
    }
  }

  async function directReject(enrollment: AgentEnrollmentRequest) {
    setSubmittingId(enrollment.enrollmentId);
    try {
      await rejectEnrollment(enrollment, 'Rejected from Agent Governance Console');
    } finally {
      setSubmittingId(null);
    }
  }

  if (loading) return <LoadingBox label="讀取 Agent enrollment 與 runtime observed candidates..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data || data.length === 0) return <EmptyState title="目前沒有 Agent enrollment" description="新的 Agent 註冊申請與 Netty runtime observed candidates 會在此合併顯示。" />;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <CommandMessage message={commandMessage} />
        <div className="sm:ml-auto">
          <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
        </div>
      </div>

      <div className="rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm text-blue-800">
        審核流程已合併到 Agent Governance。建議流程：<span className="font-semibold">Edit Draft Profile</span> → 確認 tenant / capabilities / scopes / credential → <span className="font-semibold">Approve Draft</span>。Approve 成功後，該 Agent 會成為 Core profile，其他頁面不再顯示 Create Enrollment。
      </div>

      {(sourceErrors?.coreEnrollments || sourceErrors?.coreAgents || sourceErrors?.nettyRuntimeAgents) ? (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          {sourceErrors.coreEnrollments ? <div>Core enrollments 讀取失敗：{sourceErrors.coreEnrollments}</div> : null}
          {sourceErrors.coreAgents ? <div>Core agents 讀取失敗：{sourceErrors.coreAgents}</div> : null}
          {sourceErrors.nettyRuntimeAgents ? <div>Netty runtime observed candidates 讀取失敗：{sourceErrors.nettyRuntimeAgents}</div> : null}
        </div>
      ) : null}

      <ListFilterBar
        search={search}
        searchPlaceholder="搜尋 enrollment、claimed Agent、tenant、fingerprint、remote IP..."
        onSearchChange={setSearch}
        filters={filters}
        onClear={clearFilters}
      />

      {filtered.length === 0 ? (
        <EmptyState title="沒有符合條件的 enrollment" description="請調整關鍵字或審核狀態篩選條件。" />
      ) : (
        <>
          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="whitespace-nowrap px-4 py-3">Enrollment</th>
                    <th className="whitespace-nowrap px-4 py-3">Claimed Agent</th>
                    <th className="whitespace-nowrap px-4 py-3">Tenant</th>
                    <th className="whitespace-nowrap px-4 py-3">Type</th>
                    <th className="whitespace-nowrap px-4 py-3">Status</th>
                    <th className="whitespace-nowrap px-4 py-3">Remote</th>
                    <th className="whitespace-nowrap px-4 py-3">Submitted</th>
                    <th className="whitespace-nowrap px-4 py-3">Approved / Rejected Time</th>
                    <th className="whitespace-nowrap px-4 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {pagination.items.map((enrollment) => {
                    const runtimeObserved = isRuntimeObservedEnrollment(enrollment);
                    const reviewable = canReview(enrollment);
                    const expanded = expandedId === enrollment.enrollmentId;
                    const editing = editingId === enrollment.enrollmentId;
                    const submitting = submittingId === enrollment.enrollmentId;
                    const draft = draftById[enrollment.enrollmentId] ?? buildDraft(enrollment);
                    return (
                      <tr key={enrollment.enrollmentId} className="align-top hover:bg-slate-50">
                        <td className="px-4 py-3 font-semibold text-slate-900">
                          <button
                            type="button"
                            className="text-left text-blue-700 hover:underline"
                            onClick={() => setExpandedId(expanded ? null : enrollment.enrollmentId)}
                          >
                            {enrollment.enrollmentId}
                          </button>
                          {expanded ? (
                            <div className="mt-3 w-[36rem] max-w-[80vw] space-y-3">
                              <div>
                                <div className="mb-1 text-xs font-semibold uppercase text-slate-500">Submitted metadata</div>
                                <JsonViewer value={enrollment.submittedMetadataJson ?? {}} />
                              </div>
                              <div>
                                <div className="mb-1 text-xs font-semibold uppercase text-slate-500">Evidence</div>
                                <JsonViewer value={enrollment.evidenceJson ?? {}} />
                              </div>
                            </div>
                          ) : null}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{enrollment.claimedAgentId ?? enrollment.agentName ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{enrollment.tenantId ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{enrollment.agentType ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <StatusBadge status={enrollment.status} />
                          {runtimeObserved ? <div className="mt-1 text-xs text-amber-700">Netty observed only</div> : null}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{enrollment.remoteAddress ?? '-'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(enrollment.submittedAt)}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                          {(() => {
                            const review = getReviewedTimestamp(enrollment);
                            return review.value ? (
                              <div>
                                <div className="text-xs font-semibold uppercase text-slate-400">{review.label}</div>
                                <div>{formatDateTime(review.value)}</div>
                              </div>
                            ) : '-';
                          })()}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            {reviewable ? (
                              <button
                                type="button"
                                onClick={() => openDraft(enrollment)}
                                className="rounded-lg border border-blue-200 px-2 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50"
                              >
                                Edit Draft
                              </button>
                            ) : null}
                            {reviewable ? (
                              <button
                                type="button"
                                disabled={submitting}
                                onClick={() => {
                                  if (!draft.credentialToken.trim()) {
                                    openDraft(enrollment);
                                    return;
                                  }
                                  void approveWithDraft(enrollment);
                                }}
                                title={!draft.credentialToken.trim() ? 'Credential Token is required before Approve / Approve Again. Edit the draft first.' : undefined}
                                className="rounded-lg border border-emerald-200 px-2 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-40"
                              >
                                {submitting ? 'Approving...' : isRejectedEnrollmentStatus(enrollment.status) ? 'Approve Again' : 'Approve'}
                              </button>
                            ) : null}
                            {reviewable && !isRejectedEnrollmentStatus(enrollment.status) ? (
                              <button
                                type="button"
                                disabled={submitting}
                                onClick={() => void directReject(enrollment)}
                                className="rounded-lg border border-rose-200 px-2 py-1 text-xs font-semibold text-rose-700 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-40"
                              >
                                {submitting ? 'Rejecting...' : 'Reject'}
                              </button>
                            ) : null}
                            {runtimeObserved ? (
                              <button
                                type="button"
                                disabled={submitting}
                                onClick={() => void directCreateCoreEnrollment(enrollment)}
                                className="rounded-lg border border-slate-200 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                              >
                                {submitting ? 'Creating...' : 'Create Enrollment'}
                              </button>
                            ) : null}
                            {!reviewable ? <span className="text-xs text-slate-500">Review completed</span> : null}
                          </div>
                          {editing ? (
                            <DraftEditor
                              draft={draft}
                              submitting={submitting}
                              onChange={(next) => setDraftById((current) => ({ ...current, [enrollment.enrollmentId]: next }))}
                              onApprove={() => void approveWithDraft(enrollment)}
                              capabilityCatalog={capabilityCatalog}
                              loadingCapabilities={loadingCapabilities}
                            />
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <PaginationControls
            page={pagination.page}
            pageSize={pagination.pageSize}
            totalItems={pagination.totalItems}
            totalPages={pagination.totalPages}
            startItem={pagination.startItem}
            endItem={pagination.endItem}
            onPageChange={setPage}
            onPageSizeChange={setPageSize}
          />
        </>
      )}
    </div>
  );
}
