'use client';
import { createUuid } from '@/lib/utils/uuid';

import { useState } from 'react';
import { ApiError } from '@/lib/api/errors';
import {
  approveUiAccessRequest,
  getUiAccessRequestForReview,
  rejectUiAccessRequest,
  type UiAccessRequestResponse,
} from '@/lib/api/accessRequestApi';

export function AccessRequestApprovalPanel() {
  const [requestId, setRequestId] = useState('');
  const [request, setRequest] = useState<UiAccessRequestResponse | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState<'load' | 'approve' | 'reject' | ''>('');
  const [error, setError] = useState<{ status?: number; message: string } | null>(null);
  const [message, setMessage] = useState('');

  async function load() {
    if (!requestId.trim()) return;
    setBusy('load');
    setError(null);
    setMessage('');
    try {
      setRequest(await getUiAccessRequestForReview(requestId.trim()));
    } catch (failure) {
      setRequest(null);
      setError(toReviewError(failure, 'The access request could not be loaded for review.'));
    } finally {
      setBusy('');
    }
  }

  async function decide(decision: 'approve' | 'reject') {
    if (!request || reason.trim().length < 8 || request.requesterIsCurrentUser || request.state !== 'PENDING_APPROVAL') return;
    setBusy(decision);
    setError(null);
    setMessage('');
    try {
      const review = {
        requestVersion: request.requestVersion,
        grantVersion: request.grantVersion,
        resourceVersion: request.resourceVersionAtRequest,
        reason: reason.trim(),
        idempotencyKey: createUuid(),
      };
      const next = decision === 'approve'
        ? await approveUiAccessRequest(request.requestId, review)
        : await rejectUiAccessRequest(request.requestId, review);
      setRequest(next);
      setMessage(decision === 'approve'
        ? 'The request was independently approved and the canonical Scope Grant became active.'
        : 'The request was rejected and its pending Scope Grant was revoked.');
    } catch (failure) {
      setError(toReviewError(failure, `The access request could not be ${decision}d.`));
    } finally {
      setBusy('');
    }
  }

  const blocked = !request || request.state !== 'PENDING_APPROVAL' || request.requesterIsCurrentUser || reason.trim().length < 8;
  return <section className="rounded-2xl border border-slate-200 bg-white p-5">
    <div>
      <h2 className="text-xl font-black text-slate-950">Independent Access Review</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">
        Load a governed request using reviewer authority. Approval re-resolves the server-owned action, verifies the
        canonical Scope Grant binding, checks the latest resource version, duration, visibility, and reviewer scope,
        then applies separation of duties.
      </p>
    </div>
    <div className="mt-5 flex flex-col gap-3 sm:flex-row">
      <label className="flex-1 text-sm font-black text-slate-700">Request ID
        <input value={requestId} onChange={event => setRequestId(event.target.value)}
          className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal"/>
      </label>
      <button type="button" onClick={load} disabled={!requestId.trim() || busy !== ''}
        className="self-end rounded-xl border border-slate-300 px-4 py-2.5 font-black text-slate-800 disabled:opacity-40">
        {busy === 'load' ? 'Loading…' : 'Load for review'}
      </button>
    </div>
    {request ? <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm">
      <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Item label="State" value={request.state}/><Item label="Action" value={request.uiActionId}/>
        <Item label="Resource" value={`${request.resourceType}/${request.resourceId}`}/>
        <Item label="Resource version" value={String(request.resourceVersionAtRequest)}/>
        <Item label="Request version" value={String(request.requestVersion)}/>
        <Item label="Grant version" value={String(request.grantVersion)}/>
        <Item label="Visibility" value={request.requestedVisibility}/>
        <Item label="Valid until" value={new Date(request.validTo).toLocaleString()}/>
      </dl>
      {request.requesterIsCurrentUser ? <div role="alert" className="mt-4 rounded-lg border border-rose-200 bg-rose-50 p-3 font-bold text-rose-950">
        Separation of duties: you submitted this request and cannot approve or reject it.
      </div> : null}
      {request.state !== 'PENDING_APPROVAL' ? <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-950">
        This request is no longer pending. Reload before taking another action.
      </div> : null}
    </div> : null}
    <label className="mt-4 block text-sm font-black text-slate-700">Review reason
      <textarea value={reason} onChange={event => setReason(event.target.value)} rows={3}
        className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal"
        placeholder="Document the independent decision and its business justification."/>
    </label>
    <div className="mt-4 flex flex-wrap justify-end gap-3">
      <button type="button" disabled={blocked || busy !== ''} onClick={() => decide('reject')}
        className="rounded-xl border border-rose-300 px-4 py-2.5 font-black text-rose-800 disabled:opacity-40">
        {busy === 'reject' ? 'Rejecting…' : 'Reject request'}
      </button>
      <button type="button" disabled={blocked || busy !== ''} onClick={() => decide('approve')}
        className="rounded-xl bg-indigo-700 px-4 py-2.5 font-black text-white disabled:opacity-40">
        {busy === 'approve' ? 'Approving…' : 'Approve and activate'}
      </button>
    </div>
    {message ? <div role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950">{message}</div> : null}
    {error ? <div role="alert" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-950">
      <div className="font-black">Review not completed{error.status ? ` · HTTP ${error.status}` : ''}</div>
      <p className="mt-1">{error.message}</p>
    </div> : null}
  </section>;
}

function toReviewError(failure: unknown, fallback: string) {
  const api = failure instanceof ApiError ? failure : null;
  return {
    status: api?.status,
    message: api?.status === 412
      ? 'The resource version changed. Reload the request and review the latest resource before deciding.'
      : api?.status === 409
        ? 'The request or Scope Grant changed while you were working. Reload before deciding.'
        : api?.status === 403
          ? 'You are not an eligible independent approver for this resource scope.'
          : api?.status === 404
            ? 'The access request is unavailable or outside your review scope.'
            : failure instanceof Error ? failure.message : fallback,
  };
}
function Item({ label, value }: { label: string; value: string }) { return <div><dt className="font-black text-slate-700">{label}</dt><dd className="break-all text-slate-950">{value}</dd></div>; }
