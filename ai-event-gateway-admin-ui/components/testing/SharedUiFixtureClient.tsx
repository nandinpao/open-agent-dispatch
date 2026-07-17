'use client';

import { useState } from 'react';
import { HelpText } from '@/components/common/HelpText';
import { ActionMenu } from '@/components/ui/ActionMenu';
import { AnchorButton, Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { DataTableShell, TableEmptyRow } from '@/components/ui/DataTable';
import { DebugCollapse } from '@/components/ui/DebugCollapse';
import { EmptyState } from '@/components/ui/EmptyState';
import { RightDrawer } from '@/components/ui/RightDrawer';
import { StatusBadge, statusToneFromValue } from '@/components/ui/StatusBadge';
import { AdminDetailPage, AdminSummaryCards, AdminTabLayout } from '@/components/layout';

const statuses = ['ACTIVE', 'PENDING', 'APPROVED', 'SUSPENDED', 'REVOKED', 'BLOCKED', 'RETRY_WAIT', 'DEAD_LETTER'];
const buttonTones = ['primary', 'secondary', 'warning', 'danger', 'success', 'ghost'] as const;

export function SharedUiFixtureClient() {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('components');

  const tabs = [
    { id: 'components', label: 'Components' },
    { id: 'tables', label: 'Tables' },
    { id: 'empty', label: 'Empty states' },
    { id: 'debug', label: 'Debug' },
  ];
  const activeContent = activeTab === 'tables'
    ? <TablePreview />
    : activeTab === 'empty'
      ? <EmptyStatePreview />
      : activeTab === 'debug'
        ? <DebugPreview />
        : <ComponentsPreview onOpenConfirm={() => setConfirmOpen(true)} onOpenDrawer={() => setDrawerOpen(true)} />;

  return (
    <AdminDetailPage
      eyebrow="UX-8 Preview"
      title="Shared UI Fixture"
      description="用來快速檢查 button tone、empty state、table density、tooltip、drawer、dialog、badge 與 debug collapse。此頁只作前端 QA fixture，不連後端 API。"
      actions={(
        <div className="flex flex-wrap gap-2">
          <Button tone="primary" onClick={() => setDrawerOpen(true)}>Open Drawer</Button>
          <Button tone="danger" onClick={() => setConfirmOpen(true)}>Open Confirm</Button>
        </div>
      )}
      summary={(
        <AdminSummaryCards
          items={[
            { id: 'buttons', label: 'Button tones', value: buttonTones.length, description: 'Primary / secondary / warning / danger / success / ghost', tone: 'purple' },
            { id: 'states', label: 'Status states', value: statuses.length, description: 'StatusBadge visual mapping', tone: 'info' },
            { id: 'empty', label: 'Empty states', value: '3 tones', description: 'Neutral / info / danger examples', tone: 'neutral' },
            { id: 'debug', label: 'Debug', value: 'Collapsed', description: 'Raw diagnostics stay hidden by default', tone: 'warning' },
          ]}
        />
      )}
      tabs={(
        <AdminTabLayout activeTab={activeTab} onTabChange={setActiveTab} tabs={tabs}>
          {activeContent}
        </AdminTabLayout>
      )}
    >
      <RightDrawer
        open={drawerOpen}
        title="Create / Edit Drawer"
        description="Create / Edit 不應再塞進 List 或 Detail 主頁。"
        onClose={() => setDrawerOpen(false)}
        footer={<div className="flex justify-end gap-2"><Button tone="secondary" onClick={() => setDrawerOpen(false)}>Cancel</Button><Button tone="primary" onClick={() => setDrawerOpen(false)}>Save</Button></div>}
      >
        <div className="space-y-4">
          <HelpText term="assignmentProfile" mode="block" />
          <label className="block text-sm font-bold text-slate-700">
            Example field
            <input className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" defaultValue="ERP_BUSINESS_REVIEWER" />
          </label>
        </div>
      </RightDrawer>
      <ConfirmDialog
        open={confirmOpen}
        title="Confirm danger operation"
        description="Danger / destructive actions should require an explicit confirmation and explain the impact."
        confirmLabel="Confirm"
        tone="danger"
        onConfirm={() => setConfirmOpen(false)}
        onCancel={() => setConfirmOpen(false)}
      />
    </AdminDetailPage>
  );
}

function ComponentsPreview({ onOpenConfirm, onOpenDrawer }: Readonly<{ onOpenConfirm: () => void; onOpenDrawer: () => void }>) {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="text-lg font-black text-slate-950">Button tone</h2>
        <div className="mt-4 flex flex-wrap gap-2">
          {buttonTones.map((tone) => <Button key={tone} tone={tone}>{tone}</Button>)}
          <AnchorButton href="/dispatch-flows" tone="secondary">Anchor link</AnchorButton>
        </div>
      </section>
      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="text-lg font-black text-slate-950">Status & Help</h2>
        <div className="mt-4 flex flex-wrap gap-2">
          {statuses.map((status) => <StatusBadge key={status} tone={statusToneFromValue(status)} title={status}>{status}</StatusBadge>)}
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <HelpText term="policyBinding" mode="block" />
          <HelpText action="revoke" mode="block" />
        </div>
      </section>
      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="text-lg font-black text-slate-950">Actions</h2>
        <div className="mt-4 flex flex-wrap gap-2">
          <Button tone="primary" onClick={onOpenDrawer}>Open Drawer</Button>
          <Button tone="danger" onClick={onOpenConfirm}>Open Confirm</Button>
          <ActionMenu
            items={[
              { id: 'view', label: 'View detail', description: 'Primary row action' },
              { id: 'edit', label: 'Edit in drawer', description: 'Do not inline large forms', tone: 'primary' },
              { id: 'disable', label: 'Disable', description: 'Requires confirmation', tone: 'warning' },
              { id: 'delete', label: 'Delete', description: 'Requires confirmation', tone: 'danger' },
            ]}
          />
        </div>
      </section>
    </div>
  );
}

function TablePreview() {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-black text-slate-950">Table density</h2>
          <p className="mt-1 text-sm text-slate-500">重構頁的表格應使用相近欄距、header、row action pattern。</p>
        </div>
        <Button tone="primary">Create</Button>
      </div>
      <DataTableShell className="mt-4 rounded-2xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
            <tr><th className="px-4 py-3">Object</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Updated</th><th className="px-4 py-3 text-right">Actions</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {['Supply Profile', 'Dispatch Policy', 'Agent Qualification'].map((label, index) => (
              <tr key={label} className="hover:bg-slate-50">
                <td className="px-4 py-3 font-black text-slate-900">{label}</td>
                <td className="px-4 py-3"><StatusBadge tone={index === 2 ? 'warning' : 'success'}>{index === 2 ? 'PENDING' : 'ACTIVE'}</StatusBadge></td>
                <td className="px-4 py-3 text-xs text-slate-500">2026-07-05</td>
                <td className="px-4 py-3 text-right"><ActionMenu items={[{ id: 'view', label: 'View' }, { id: 'edit', label: 'Edit', tone: 'primary' }]} /></td>
              </tr>
            ))}
            <TableEmptyRow colSpan={4} title="Optional empty row preview" description="TableEmptyRow should keep the same visual rhythm as populated rows." />
          </tbody>
        </table>
      </DataTableShell>
    </section>
  );
}

function EmptyStatePreview() {
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <EmptyState title="No profiles found" description="Create a profile or clear filters." nextAction="Create / Edit should open a drawer." tone="info" />
      <EmptyState title="No debug data" description="Debug sections stay collapsed until an engineer needs them." tone="neutral" />
      <EmptyState title="Action blocked" description="Dangerous or blocked actions must explain why." nextAction="Check impact preview before disabling profiles." tone="danger" />
    </div>
  );
}

function DebugPreview() {
  return (
    <DebugCollapse title="Raw diagnostics" summary="Collapsed by default">
      <pre className="overflow-auto rounded-xl bg-slate-950 p-4 text-xs leading-5 text-slate-100">{JSON.stringify({ status: 'BLOCKED', reason: 'DISPATCH_AGENT_PROFILE_MISSING', nextAction: 'Open Supply Profiles' }, null, 2)}</pre>
    </DebugCollapse>
  );
}
