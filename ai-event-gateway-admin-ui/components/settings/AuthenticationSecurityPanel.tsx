'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { authApi } from '@/lib/api/authApi';
import type { AdminSecurityAuditEvent, AdminSessionDescriptor } from '@/lib/types/admin';
import { formatDateTime } from '@/lib/utils/format';

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unable to load authentication security information.';
}

function outcomeClass(outcome: string): string {
  if (outcome === 'SUCCESS') return 'bg-emerald-100 text-emerald-700';
  if (outcome === 'DENIED' || outcome === 'FAILED') return 'bg-rose-100 text-rose-700';
  return 'bg-slate-100 text-slate-700';
}

export function AuthenticationSecurityPanel() {
  const { user, hasRole, logout } = useAuth();
  const [sessions, setSessions] = useState<AdminSessionDescriptor[]>([]);
  const [auditEvents, setAuditEvents] = useState<AdminSecurityAuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmReference, setConfirmReference] = useState<string | null>(null);
  const [revokingReference, setRevokingReference] = useState<string | null>(null);
  const isAdmin = hasRole('ADMIN');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [sessionResponse, auditResponse] = await Promise.all([
        authApi.sessions(),
        isAdmin ? authApi.securityAudit(50) : Promise.resolve({ events: [] })
      ]);
      setSessions(sessionResponse.sessions);
      setAuditEvents(auditResponse.events);
    } catch (loadError) {
      setError(errorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    void load();
  }, [load, user?.selectedTenantId]);

  const activeCount = useMemo(() => sessions.filter((session) => !session.expired).length, [sessions]);

  const revoke = useCallback(async (session: AdminSessionDescriptor) => {
    setRevokingReference(session.sessionReference);
    setError(null);
    try {
      await authApi.revokeSession(session.sessionReference);
      setConfirmReference(null);
      if (session.current) {
        await logout();
        return;
      }
      await load();
    } catch (revokeError) {
      setError(errorMessage(revokeError));
    } finally {
      setRevokingReference(null);
    }
  }, [load, logout]);

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-sm font-bold text-slate-950">Authentication security</div>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">
            Core owns Human Admin sessions and tenant authority. Session references are one-way values; raw Redis session identifiers and machine credentials are never exposed to the browser.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">{activeCount} active sessions</span>
          <button
            type="button"
            onClick={() => void load()}
            disabled={loading}
            className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </div>

      {error ? <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div> : null}

      <div className="overflow-hidden rounded-xl border border-slate-200">
        <div className="border-b border-slate-200 bg-slate-50 px-4 py-3">
          <div className="text-sm font-semibold text-slate-900">Server-side sessions</div>
          <div className="mt-1 text-xs text-slate-500">{isAdmin ? 'ADMIN can inspect and revoke sessions for configured Human Admin accounts.' : 'You can inspect and revoke only your own sessions.'}</div>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-white text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3 text-left">Account</th>
                <th className="px-4 py-3 text-left">Tenant</th>
                <th className="px-4 py-3 text-left">Last accessed</th>
                <th className="px-4 py-3 text-left">Expires</th>
                <th className="px-4 py-3 text-left">Session reference</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sessions.map((session) => (
                <tr key={session.sessionReference} className="align-top">
                  <td className="px-4 py-3">
                    <div className="font-semibold text-slate-900">{session.username}</div>
                    <div className="mt-1 flex gap-1 text-[11px]">
                      {session.current ? <span className="rounded-full bg-blue-100 px-2 py-0.5 font-semibold text-blue-700">CURRENT</span> : null}
                      {session.expired ? <span className="rounded-full bg-slate-100 px-2 py-0.5 font-semibold text-slate-600">EXPIRED</span> : null}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-700">{session.selectedTenantId || '-'}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(session.lastAccessedAt)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(session.expiresAt)}</td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-500">{session.sessionReference}</td>
                  <td className="px-4 py-3 text-right">
                    {confirmReference === session.sessionReference ? (
                      <div className="flex justify-end gap-2">
                        <button type="button" onClick={() => setConfirmReference(null)} className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50">Cancel</button>
                        <button
                          type="button"
                          onClick={() => void revoke(session)}
                          disabled={revokingReference === session.sessionReference}
                          className="rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-700 disabled:opacity-50"
                        >
                          {revokingReference === session.sessionReference ? 'Revoking...' : 'Confirm revoke'}
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmReference(session.sessionReference)}
                        className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-50"
                      >
                        {session.current ? 'Revoke and sign out' : 'Revoke'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {!loading && sessions.length === 0 ? (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-sm text-slate-500">No active server-side sessions were found.</td></tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      {isAdmin ? (
        <div className="overflow-hidden rounded-xl border border-slate-200">
          <div className="border-b border-slate-200 bg-slate-50 px-4 py-3">
            <div className="text-sm font-semibold text-slate-900">Authentication security audit</div>
            <div className="mt-1 text-xs text-slate-500">Recent login, logout, tenant-selection, and session-revocation evidence. Production retention is provided by the structured security-log sink.</div>
          </div>
          <div className="max-h-[28rem] overflow-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="sticky top-0 bg-white text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3 text-left">Time</th>
                  <th className="px-4 py-3 text-left">Event</th>
                  <th className="px-4 py-3 text-left">Outcome</th>
                  <th className="px-4 py-3 text-left">Account / Tenant</th>
                  <th className="px-4 py-3 text-left">Source</th>
                  <th className="px-4 py-3 text-left">Reason</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {auditEvents.map((event) => (
                  <tr key={event.eventId} className="align-top">
                    <td className="whitespace-nowrap px-4 py-3 text-xs text-slate-600">{formatDateTime(event.occurredAt)}</td>
                    <td className="px-4 py-3 font-semibold text-slate-900">{event.eventType}</td>
                    <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-[11px] font-semibold ${outcomeClass(event.outcome)}`}>{event.outcome}</span></td>
                    <td className="px-4 py-3 text-slate-700"><div>{event.username || '-'}</div><div className="text-xs text-slate-500">{event.tenantId || '-'}</div></td>
                    <td className="px-4 py-3 text-xs text-slate-600"><div>{event.sourceAddress || '-'}</div><div className="max-w-xs truncate" title={event.userAgent}>{event.userAgent || '-'}</div></td>
                    <td className="max-w-md px-4 py-3 text-xs text-slate-600">{event.reason || '-'}</td>
                  </tr>
                ))}
                {!loading && auditEvents.length === 0 ? (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-sm text-slate-500">No authentication audit events are currently available on this Core node.</td></tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </section>
  );
}
