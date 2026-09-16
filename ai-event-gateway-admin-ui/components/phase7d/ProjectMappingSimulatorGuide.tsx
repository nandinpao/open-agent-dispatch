export function ProjectMappingSimulatorGuide() {
  return (
    <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5" aria-labelledby="mapping-simulator-guide-title">
      <h3 id="mapping-simulator-guide-title" className="font-black text-indigo-950">Project mapping simulator</h3>
      <p className="mt-1 text-sm leading-6 text-indigo-900">Validate the selected project, issue type, required fields, templates, principal, context policy, result policy, and fallback before publishing a mapping.</p>
      <ol className="mt-3 grid gap-3 text-sm md:grid-cols-3">
        <li className="rounded-xl border border-indigo-200 bg-white p-3"><strong>1. Provider metadata</strong><br />Probe accessible projects, issue types, fields, transitions, and link support.</li>
        <li className="rounded-xl border border-indigo-200 bg-white p-3"><strong>2. Safe preview</strong><br />Render summary and field mapping with synthetic values; do not expose a real Task payload.</li>
        <li className="rounded-xl border border-indigo-200 bg-white p-3"><strong>3. Publish gate</strong><br />Block activation when required fields, schema hash, permission probe, or principal scope is stale.</li>
      </ol>
    </section>
  );
}
