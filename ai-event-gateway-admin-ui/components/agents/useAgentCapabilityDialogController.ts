'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CommandResult } from '@/lib/types/admin';
import type { CoreAgentCapabilityCommand, CoreCanonicalCapabilityDefinition } from '@/lib/types/core';
import { normalizeCode } from '@/components/agents/AgentDetailUi';

type CommandFn<TBody> = (body: TBody) => Promise<CommandResult>;

export function useAgentCapabilityDialogController({
  agentId,
  tenantId,
  open,
  existingCodes,
  requestAgentCapability,
  onChanged,
}: Readonly<{
  agentId: string;
  tenantId: string;
  open: boolean;
  existingCodes: string[];
  requestAgentCapability: CommandFn<CoreAgentCapabilityCommand>;
  onChanged: () => Promise<void> | void;
}>) {
  const [catalog, setCatalog] = useState<CoreCanonicalCapabilityDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [selectedCode, setSelectedCode] = useState('');
  const [reason, setReason] = useState('Assigned from Agent detail inline management.');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadCatalog = useCallback(async (preferredCode?: string) => {
    if (!tenantId.trim()) {
      setError('Tenant is required before loading capabilities.');
      setCatalog([]);
      return;
    }
    const items = await coreAdminApi.getCanonicalCapabilities('ACTIVE', undefined, undefined, tenantId);
    setCatalog(items);
    setSelectedCode((current) => preferredCode || current || items[0]?.capabilityCode || '');
  }, [tenantId]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    setMessage(null);
    setError(null);
    void loadCatalog()
      .catch((caught) => { if (!cancelled) setError(caught instanceof Error ? caught.message : String(caught)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [loadCatalog, open]);

  const existing = useMemo(() => new Set(existingCodes.map(normalizeCode)), [existingCodes]);
  const availableCatalog = useMemo(
    () => catalog.filter((capability) => !existing.has(normalizeCode(capability.capabilityCode))),
    [catalog, existing],
  );

  async function submit(validationMessage: string) {
    if (!selectedCode) {
      setError(validationMessage);
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await requestAgentCapability({
        tenantId,
        capabilityCode: selectedCode,
        source: 'AGENT_DETAIL_INLINE_MANAGEMENT',
        evidenceRef: `agent-detail:${agentId}`,
        reason,
        operatorId: 'admin-ui',
      });
      await onChanged();
      return selectedCode;
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : String(caught));
      return undefined;
    } finally {
      setSaving(false);
    }
  }

  async function capabilityCreated(created: CoreCanonicalCapabilityDefinition) {
    try {
      await loadCatalog(created.capabilityCode);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : String(caught));
    }
  }

  return {
    availableCatalog,
    loading,
    saving,
    selectedCode,
    reason,
    message,
    error,
    setSelectedCode,
    setReason,
    setMessage,
    submit,
    capabilityCreated,
  };
}
