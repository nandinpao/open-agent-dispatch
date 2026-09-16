'use client';

import { RightDrawer } from '@/components/ui/RightDrawer';
import { handoffContextCopy, type FieldShareDecision } from '@/lib/handoffContextContract';

export interface HandoffPreviewField {
  fieldPath: string;
  decision: FieldShareDecision;
  displayedValue?: unknown;
  reason?: string;
}

export interface HandoffPreviewModel {
  sourceTaskId: string;
  targetTaskId: string;
  targetDepartment?: string;
  targetProject?: string;
  summary?: string;
  approvalRequired: boolean;
  fields: HandoffPreviewField[];
  attachments: Array<{ filename: string; contentType?: string; sizeBytes?: number; sha256?: string }>;
}

export function HandoffPreviewDrawer({
  open,
  preview,
  onClose,
  onCreate,
}: Readonly<{ open: boolean; preview: HandoffPreviewModel | null; onClose: () => void; onCreate: () => void }>) {
  if (!preview) return null;
  const groups = (decision: FieldShareDecision) => preview.fields.filter((field) => field.decision === decision);
  return (
    <RightDrawer
      open={open}
      onClose={onClose}
      title={handoffContextCopy.title}
      description={`${preview.sourceTaskId} → ${preview.targetTaskId}`}
      footer={(
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-white focus:outline-none focus:ring-2 focus:ring-blue-200">Cancel</button>
          <button type="button" onClick={onCreate} className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-bold text-white hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-400 focus:ring-offset-2">Create Snapshot</button>
        </div>
      )}
    >
      <dl className="grid gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm sm:grid-cols-2">
        <div><dt className="text-slate-500">Target Department</dt><dd className="mt-1 font-bold text-slate-900">{preview.targetDepartment ?? 'Not resolved'}</dd></div>
        <div><dt className="text-slate-500">Target Project</dt><dd className="mt-1 font-bold text-slate-900">{preview.targetProject ?? 'Not resolved'}</dd></div>
      </dl>
      <section className="mt-6" aria-labelledby="handoff-summary-heading">
        <h3 id="handoff-summary-heading" className="font-bold text-slate-950">Summary</h3>
        <p className="mt-2 whitespace-pre-wrap rounded-xl bg-slate-50 p-3 text-sm text-slate-700">{preview.summary || 'No summary will be shared.'}</p>
      </section>
      <DecisionSection title={handoffContextCopy.shared} fields={[...groups('ALLOW'), ...groups('MASK')]} />
      <DecisionSection title={handoffContextCopy.omitted} fields={[...groups('OMIT'), ...groups('REQUIRE_APPROVAL')]} />
      <section className="mt-6" aria-labelledby="handoff-attachments-heading">
        <h3 id="handoff-attachments-heading" className="font-bold text-slate-950">{handoffContextCopy.attachments}</h3>
        <p className="mt-1 text-xs text-slate-500">{handoffContextCopy.metadataOnly}</p>
        {preview.attachments.length === 0 ? <p className="mt-3 text-sm text-slate-500">No attachment metadata will be shared.</p> : (
          <ul className="mt-3 space-y-2">
            {preview.attachments.map((attachment) => (
              <li key={`${attachment.filename}-${attachment.sha256 ?? ''}`} className="rounded-xl border border-slate-200 p-3 text-sm">
                <strong>{attachment.filename}</strong>
                <div className="mt-1 text-slate-500">{attachment.contentType ?? 'Unknown type'} · {attachment.sizeBytes ?? 0} bytes</div>
              </li>
            ))}
          </ul>
        )}
      </section>
      {preview.approvalRequired ? <p className="mt-6 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" role="status">{handoffContextCopy.approvalRequired}</p> : null}
    </RightDrawer>
  );
}

function DecisionSection({ title, fields }: Readonly<{ title: string; fields: HandoffPreviewField[] }>) {
  return (
    <section className="mt-6">
      <h3 className="font-bold text-slate-950">{title}</h3>
      {fields.length === 0 ? <p className="mt-2 text-sm text-slate-500">None</p> : (
        <ul className="mt-2 divide-y divide-slate-100 rounded-xl border border-slate-200">
          {fields.map((field) => (
            <li key={field.fieldPath} className="flex items-start justify-between gap-4 p-3 text-sm">
              <div><strong>{field.fieldPath}</strong>{field.reason ? <p className="mt-1 text-slate-500">{field.reason}</p> : null}</div>
              <span className="rounded-lg bg-slate-100 px-2 py-1 text-xs font-bold text-slate-700">{field.decision}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
