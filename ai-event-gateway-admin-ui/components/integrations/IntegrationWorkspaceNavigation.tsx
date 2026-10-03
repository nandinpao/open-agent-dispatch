'use client';

import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Tabs, type TabDefinition } from '@/components/ui/Tabs';
import type { IntegrationConnection, IntegrationProjectMapping, ProviderType } from '@/lib/api/domains/integrationIdentityApi';

export type IntegrationWorkspaceTab = 'overview' | 'authentication' | 'mappings' | 'runtime';

const PROVIDER_LABELS: Record<ProviderType, string> = {
  REDMINE: 'Redmine',
  JIRA: 'Jira',
  GITLAB_ISSUES: 'GitLab Issues',
};

function connectionMappings(connectionId: string, mappings: IntegrationProjectMapping[]) {
  return mappings.filter((mapping) => mapping.connectionId === connectionId);
}

function connectionState(connection: IntegrationConnection, mappings: IntegrationProjectMapping[]) {
  const related = connectionMappings(connection.connectionId, mappings);
  const active = related.filter((mapping) => mapping.lifecycleStatus === 'ACTIVE' && mapping.enabled !== false);
  if (!connection.enabled || String(connection.status ?? '').toUpperCase() !== 'ACTIVE') {
    return { label: 'Disabled', tone: 'neutral', active: active.length, sources: 0 } as const;
  }
  if (!active.length) {
    return { label: 'Needs mapping', tone: 'warning', active: 0, sources: 0 } as const;
  }
  const sources = new Set(active.map((mapping) => mapping.sourceSystemId).filter(Boolean)).size;
  return { label: 'Configured', tone: 'success', active: active.length, sources } as const;
}

export function IntegrationConnectionNavigator({ connections, mappings, selectedConnectionId, onSelect, onCreate }: Readonly<{
  connections: IntegrationConnection[];
  mappings: IntegrationProjectMapping[];
  selectedConnectionId: string;
  onSelect: (connectionId: string) => void;
  onCreate: () => void;
}>) {
  return <aside className="rounded-2xl border border-slate-200 bg-white p-4 xl:sticky xl:top-4 xl:self-start">
    <div className="flex items-start justify-between gap-3">
      <div>
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">Connections</div>
        <h2 className="mt-1 font-black text-slate-950">Issue Tracking providers</h2>
        <p className="mt-1 text-xs leading-5 text-slate-500">Choose one connection to configure. Status here is configuration state, not a live provider-health guarantee.</p>
      </div>
      <Button type="button" tone="primary" size="xs" onClick={onCreate}>+ Add</Button>
    </div>

    <div className="mt-4 space-y-2" role="list" aria-label="Issue Tracking connections">
      {connections.length ? connections.map((connection) => {
        const state = connectionState(connection, mappings);
        const selected = connection.connectionId === selectedConnectionId;
        return <div key={connection.connectionId} role="listitem">
          <button
            type="button"
            aria-current={selected ? 'true' : undefined}
            onClick={() => onSelect(connection.connectionId)}
            className={`w-full rounded-xl border p-3 text-left transition ${selected ? 'border-slate-950 bg-slate-950 text-white shadow-sm' : 'border-slate-200 bg-white text-slate-900 hover:border-slate-400 hover:bg-slate-50'}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className={`text-[10px] font-black uppercase tracking-wide ${selected ? 'text-slate-300' : 'text-slate-500'}`}>{PROVIDER_LABELS[connection.providerType]}</div>
                <div className="mt-1 truncate text-sm font-black">{connection.connectionName || connection.connectionId}</div>
              </div>
              <StatusBadge tone={selected ? 'purple' : state.tone}>{state.label}</StatusBadge>
            </div>
            <div className={`mt-2 text-[11px] leading-5 ${selected ? 'text-slate-300' : 'text-slate-500'}`}>
              {state.active} active mapping{state.active === 1 ? '' : 's'}{state.sources ? ` · ${state.sources} Source System${state.sources === 1 ? '' : 's'}` : ''}
            </div>
          </button>
        </div>;
      }) : <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-600">No Issue Tracking connection exists yet. Add Redmine or Jira to begin.</div>}
    </div>
  </aside>;
}

const BASE_TABS: ReadonlyArray<Omit<TabDefinition<IntegrationWorkspaceTab>, 'badge' | 'attentionLabel'>> = [
  { id: 'overview', label: 'Overview', description: 'Endpoint and current configuration' },
  { id: 'authentication', label: 'Authentication', description: 'Technical identity and safe access check' },
  { id: 'mappings', label: 'Mappings', description: 'Source / Task routing and governed publish' },
  { id: 'runtime', label: 'Runtime Health', description: 'Operational evidence and recovery handoff' },
];

export function IntegrationWorkspaceTabs({ value, onChange, mappingCount = 0, attention }: Readonly<{
  value: IntegrationWorkspaceTab;
  onChange: (value: IntegrationWorkspaceTab) => void;
  mappingCount?: number;
  attention?: boolean;
}>) {
  const tabs: Array<TabDefinition<IntegrationWorkspaceTab>> = BASE_TABS.map((tab) => ({
    ...tab,
    badge: tab.id === 'mappings' && mappingCount > 0 ? <StatusBadge tone="neutral">{mappingCount}</StatusBadge> : undefined,
    attentionLabel: tab.id === 'runtime' && attention ? 'Runtime health needs attention' : undefined,
  }));
  return <Tabs tabs={tabs} value={value} onChange={onChange} ariaLabel="Integration configuration sections" idPrefix="integration" />;
}
