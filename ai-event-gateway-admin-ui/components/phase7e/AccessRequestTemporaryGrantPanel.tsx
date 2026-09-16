'use client';
import { createUuid } from '@/lib/utils/uuid';

import { useMemo, useState } from 'react';
import { ApiError } from '@/lib/api/errors';
import { submitUiAccessRequest, type UiAccessRequestResponse } from '@/lib/api/accessRequestApi';
import { durationPresetHours, validateAccessRequest, type GrantDurationPreset } from '@/lib/phase7e/iamResourceAccessUx';

const presets: GrantDurationPreset[] = ['EIGHT_HOURS', 'ONE_DAY', 'SEVEN_DAYS', 'THIRTY_DAYS'];
const actions = ['task.detail.view', 'task.detail.update', 'task.retry.execute'] as const;

export function AccessRequestTemporaryGrantPanel() {
  const [requestedAction, setRequestedAction] = useState<(typeof actions)[number]>('task.detail.update');
  const [resourceId, setResourceId] = useState('');
  const [resourceVersion, setResourceVersion] = useState('');
  const [purpose, setPurpose] = useState('');
  const [preset, setPreset] = useState<GrantDurationPreset>('ONE_DAY');
  const [visibility, setVisibility] = useState<'METADATA' | 'SUMMARY' | 'STANDARD'>('STANDARD');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<UiAccessRequestResponse | null>(null);
  const [error, setError] = useState<{ status?: number; message: string } | null>(null);

  const durationHours = durationPresetHours(preset);
  const expectedResourceVersion = Number(resourceVersion);
  const validation = useMemo(() => {
    const base = validateAccessRequest({
      requestedAction,
      resourceType: 'SERVER_RESOLVED',
      resourceId,
      businessPurpose: purpose,
      durationHours,
      requestedVisibility: visibility,
    });
    const issues = [...base.issues];
    if (!Number.isSafeInteger(expectedResourceVersion) || expectedResourceVersion < 1) {
      issues.push('Current resource version is required. Reload the resource before submitting.');
    }
    return {
      ...base,
      issues,
      status: issues.length ? ('BLOCKED' as const) : base.status,
      safestNextAction: issues.length
        ? 'Correct the blocked fields or reload the current resource version before submission.'
        : base.safestNextAction,
    };
  }, [requestedAction, resourceId, purpose, durationHours, visibility, expectedResourceVersion]);

  async function submit() {
    if (validation.status === 'BLOCKED') return;
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const response = await submitUiAccessRequest({
        uiActionId: requestedAction,
        resourceId: resourceId.trim(),
        expectedResourceVersion,
        requestedVisibility: visibility,
        durationHours,
        businessPurpose: purpose.trim(),
      }, createUuid());
      setResult(response);
    } catch (failure) {
      const api = failure instanceof ApiError ? failure : null;
      setError({
        status: api?.status,
        message: api?.status === 412
          ? 'The resource changed. Reload its latest version and review the request again.'
          : api?.status === 409
            ? 'The request changed while you were working. Reload before retrying.'
            : api?.status === 403
              ? 'This action, visibility, or resource scope is not requestable.'
              : failure instanceof Error ? failure.message : 'The access request could not be submitted.',
      });
    } finally {
      setSubmitting(false);
    }
  }

  return <section className="rounded-2xl border border-slate-200 bg-white p-5">
    <div>
      <h2 className="text-xl font-black text-slate-950">Temporary Access Request</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">
        Submit a least-privilege request using a server-defined UI action. The server resolves the canonical permission,
        resource type, requester identity, and RESOURCE scope. Submission creates a Scope Grant draft and moves it to
        independent approval; it does not activate access.
      </p>
    </div>
    <div className="mt-5 grid gap-4 md:grid-cols-2">
      <Select label="Needed action" value={requestedAction} values={actions} onChange={value => setRequestedAction(value as (typeof actions)[number])}/>
      <Field label="Resource ID" value={resourceId} onChange={setResourceId}/>
      <Field label="Current resource version" value={resourceVersion} onChange={setResourceVersion} inputMode="numeric"/>
      <Select label="Requested visibility" value={visibility} values={['METADATA', 'SUMMARY', 'STANDARD']} onChange={value => setVisibility(value as typeof visibility)}/>
      <Select label="Duration" value={preset} values={presets} onChange={value => setPreset(value as GrantDurationPreset)}/>
    </div>
    <label className="mt-4 block text-sm font-black text-slate-700">Business purpose
      <textarea value={purpose} onChange={event => setPurpose(event.target.value)} rows={4}
        className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal"
        placeholder="Explain why access is required, the work being performed, and when access should end."/>
    </label>
    <div className={`mt-4 rounded-xl border p-4 text-sm ${validation.status === 'BLOCKED' ? 'border-rose-200 bg-rose-50 text-rose-950' : validation.status === 'WARNING' ? 'border-amber-200 bg-amber-50 text-amber-950' : 'border-emerald-200 bg-emerald-50 text-emerald-950'}`}>
      <div className="font-black">Request validation · {validation.status}</div>
      <p className="mt-1">{validation.safestNextAction}</p>
      {validation.issues.length ? <ul className="mt-2 list-disc pl-5">{validation.issues.map(issue => <li key={issue}>{issue}</li>)}</ul> : null}
    </div>
    <div className="mt-5 flex justify-end">
      <button type="button" disabled={validation.status === 'BLOCKED' || submitting} onClick={submit}
        className="rounded-xl bg-indigo-700 px-5 py-2.5 font-black text-white disabled:opacity-40">
        {submitting ? 'Submitting…' : 'Submit for approval'}
      </button>
    </div>
    {error ? <div role="alert" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-950">
      <div className="font-black">Request not submitted{error.status ? ` · HTTP ${error.status}` : ''}</div>
      <p className="mt-1">{error.message}</p>
    </div> : null}
    {result ? <div className="mt-4 rounded-xl border border-indigo-200 bg-indigo-50 p-4 text-sm text-indigo-950">
      <div className="font-black">Access request submitted</div>
      <dl className="mt-2 grid gap-2 sm:grid-cols-2">
        <Item label="Request" value={result.requestId}/><Item label="State" value={result.state}/>
        <Item label="Action" value={result.uiActionId}/><Item label="Resource" value={`${result.resourceType}/${result.resourceId}`}/>
        <Item label="Resource version" value={String(result.resourceVersionAtRequest)}/><Item label="Valid until" value={new Date(result.validTo).toLocaleString()}/>
      </dl>
      <p className="mt-3 leading-6">State <strong>{result.state}</strong> is not active access. A different authorized approver must revalidate the current resource, scope, visibility, duration, and Scope Grant version before activation.</p>
    </div> : null}
  </section>;
}

function Field({ label, value, onChange, inputMode }: { label: string; value: string; onChange: (value: string) => void; inputMode?: 'numeric' }) {
  return <label className="text-sm font-black text-slate-700">{label}<input value={value} inputMode={inputMode}
    onChange={event => onChange(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal"/></label>;
}
function Select({ label, value, values, onChange }: { label: string; value: string; values: readonly string[]; onChange: (value: string) => void }) {
  return <label className="text-sm font-black text-slate-700">{label}<select value={value} onChange={event => onChange(event.target.value)}
    className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal">{values.map(item => <option key={item} value={item}>{item.replaceAll('_', ' ')}</option>)}</select></label>;
}
function Item({ label, value }: { label: string; value: string }) { return <div><dt className="font-black">{label}</dt><dd className="break-all">{value}</dd></div>; }
