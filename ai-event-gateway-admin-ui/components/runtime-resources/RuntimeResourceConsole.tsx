"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { StatusBadge } from "@/components/common/StatusBadge";
import { PageHeader } from "@/components/common/PageHeader";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import type { CoreRuntimeResource } from "@/lib/types/core";

const inputClass = "mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm";

function normalizeRuntimeCode(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

function runtimeIdFromCode(code: string): string {
  return `runtime-${normalizeRuntimeCode(code)}`;
}

export function RuntimeResourceConsole() {
  const [resources, setResources] = useState<CoreRuntimeResource[]>([]);
  const [selectedRuntimeId, setSelectedRuntimeId] = useState<string | null>(null);
  const [draft, setDraft] = useState<CoreRuntimeResource>({ runtimeId: "", runtimeCode: "", runtimeType: "RUNTIME", environment: "default", trustStatus: "UNVERIFIED", status: "REGISTERED", capacityLimit: 1 });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const next = await coreAdminApi.getRuntimeResources();
      setResources(next);
      setSelectedRuntimeId((current) => {
        if (!current && next[0]) {
          setDraft(next[0]);
          return next[0].runtimeId;
        }
        return current;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  const selected = useMemo(() => resources.find((item) => item.runtimeId === selectedRuntimeId), [resources, selectedRuntimeId]);

  function select(resource: CoreRuntimeResource) {
    setSelectedRuntimeId(resource.runtimeId);
    setDraft(resource);
    setError(null);
    setMessage(null);
  }

  function createNew() {
    setSelectedRuntimeId(null);
    setDraft({ runtimeId: "", runtimeCode: "", runtimeType: "RUNTIME", environment: "default", trustStatus: "UNVERIFIED", status: "REGISTERED", capacityLimit: 1 });
    setError(null);
    setMessage(null);
  }

  async function save() {
    const runtimeCode = normalizeRuntimeCode(draft.runtimeCode || draft.runtimeId || "");
    if (!runtimeCode) {
      setError("runtimeCode is required.");
      return;
    }
    const runtimeId = draft.runtimeId?.trim() || runtimeIdFromCode(runtimeCode);
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const saved = await coreAdminApi.upsertRuntimeResource(runtimeId, { ...draft, runtimeId, runtimeCode });
      setMessage(`Runtime Resource ${saved.runtimeCode ?? saved.runtimeId} saved.`);
      await reload();
      setSelectedRuntimeId(saved.runtimeId);
      setDraft(saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Runtime Resources"
        description="P3-C Supply Model：正式管理 runtime / connector / execution host。Agent 必須透過 Agent Runtime Binding 綁定此資源後，才形成可派工供給。"
      />

      {message ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-700">{message}</div> : null}
      {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700">{error}</div> : null}

      <div className="grid gap-5 xl:grid-cols-[420px_minmax(0,1fr)]">
        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h2 className="font-black text-slate-900">Runtime Resources</h2>
              <p className="mt-1 text-xs text-slate-500">Runtime observation is not dispatch authority. Binding is required.</p>
            </div>
            <Button tone="primary" onClick={createNew}>Create</Button>
          </div>
          {loading ? <div className="text-sm text-slate-500">Loading...</div> : resources.length ? (
            <div className="space-y-2">
              {resources.map((resource) => (
                <button key={resource.runtimeId} type="button" onClick={() => select(resource)} className={`w-full rounded-2xl border p-4 text-left ${selected?.runtimeId === resource.runtimeId ? "border-blue-300 bg-blue-50" : "border-slate-200 bg-white hover:bg-slate-50"}`}>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="font-black text-slate-900">{resource.runtimeCode ?? resource.runtimeId}</div>
                      <div className="mt-1 text-xs text-slate-500">{resource.runtimeName ?? "-"}</div>
                    </div>
                    <StatusBadge status={resource.status ?? "REGISTERED"} />
                  </div>
                  <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-600">
                    <div>{resource.runtimeType ?? "RUNTIME"}</div>
                    <div>{resource.trustStatus ?? "UNVERIFIED"}</div>
                    <div>{resource.region ?? "default"}</div>
                    <div>capacity {resource.capacityLimit ?? 1}</div>
                  </div>
                </button>
              ))}
            </div>
          ) : <EmptyState title="No runtime resources" description="Create Runtime Resources before binding Agents to runtime supply." />}
        </section>

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="font-black text-slate-900">{selected ? "Edit Runtime Resource" : "Create Runtime Resource"}</h2>
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <label className="text-sm font-bold text-slate-700">Runtime Code<input className={inputClass} value={draft.runtimeCode ?? ""} onChange={(event) => setDraft((current) => ({ ...current, runtimeCode: normalizeRuntimeCode(event.target.value), runtimeId: current.runtimeId || runtimeIdFromCode(event.target.value) }))} placeholder="openclaw-prod-worker-01" /></label>
            <label className="text-sm font-bold text-slate-700">Runtime Name<input className={inputClass} value={draft.runtimeName ?? ""} onChange={(event) => setDraft((current) => ({ ...current, runtimeName: event.target.value }))} /></label>
            <label className="text-sm font-bold text-slate-700">Runtime Type<input className={inputClass} value={draft.runtimeType ?? "RUNTIME"} onChange={(event) => setDraft((current) => ({ ...current, runtimeType: event.target.value.toUpperCase() }))} /></label>
            <label className="text-sm font-bold text-slate-700">Connector Type<input className={inputClass} value={draft.connectorType ?? ""} onChange={(event) => setDraft((current) => ({ ...current, connectorType: event.target.value }))} /></label>
            <label className="text-sm font-bold text-slate-700">Execution Host<input className={inputClass} value={draft.executionHost ?? ""} onChange={(event) => setDraft((current) => ({ ...current, executionHost: event.target.value }))} /></label>
            <label className="text-sm font-bold text-slate-700">Environment<input className={inputClass} value={draft.environment ?? "default"} onChange={(event) => setDraft((current) => ({ ...current, environment: event.target.value }))} /></label>
            <label className="text-sm font-bold text-slate-700">Region<input className={inputClass} value={draft.region ?? ""} onChange={(event) => setDraft((current) => ({ ...current, region: event.target.value }))} /></label>
            <label className="text-sm font-bold text-slate-700">Zone<input className={inputClass} value={draft.zone ?? ""} onChange={(event) => setDraft((current) => ({ ...current, zone: event.target.value }))} /></label>
            <label className="text-sm font-bold text-slate-700">Trust<select className={inputClass} value={draft.trustStatus ?? "UNVERIFIED"} onChange={(event) => setDraft((current) => ({ ...current, trustStatus: event.target.value }))}><option>UNVERIFIED</option><option>VERIFIED</option><option>TRUSTED</option><option>UNTRUSTED</option><option>SUSPENDED</option><option>REVOKED</option></select></label>
            <label className="text-sm font-bold text-slate-700">Status<select className={inputClass} value={draft.status ?? "REGISTERED"} onChange={(event) => setDraft((current) => ({ ...current, status: event.target.value }))}><option>REGISTERED</option><option>ACTIVE</option><option>SUSPENDED</option><option>OFFLINE</option><option>RETIRED</option></select></label>
            <label className="text-sm font-bold text-slate-700">Capacity Limit<input type="number" min={0} className={inputClass} value={draft.capacityLimit ?? 1} onChange={(event) => setDraft((current) => ({ ...current, capacityLimit: Number(event.target.value) }))} /></label>
          </div>
          <div className="mt-5 flex justify-end gap-2 border-t border-slate-100 pt-4"><Button tone="primary" disabled={saving} onClick={() => void save()}>{saving ? "Saving..." : "Save Runtime Resource"}</Button></div>
        </section>
      </div>
    </div>
  );
}
