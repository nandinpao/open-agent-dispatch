const SENSITIVE_KEY_PATTERN = /token|authorization|password|secret|api[-_]?key|refresh/i;
const MAX_JSON_CHARS = 20_000;

function redactAndClone(value: unknown, seen = new WeakSet<object>()): unknown {
  if (value === null || typeof value !== 'object') return value;
  if (seen.has(value)) return '[Circular]';
  seen.add(value);

  if (Array.isArray(value)) {
    return value.map((item) => redactAndClone(item, seen));
  }

  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, item]) => [
      key,
      SENSITIVE_KEY_PATTERN.test(key) ? '[REDACTED]' : redactAndClone(item, seen)
    ])
  );
}

function safeStringify(value: unknown): string {
  try {
    const serialized = JSON.stringify(redactAndClone(value), null, 2) ?? '';
    if (serialized.length <= MAX_JSON_CHARS) return serialized;
    return `${serialized.slice(0, MAX_JSON_CHARS)}\n... [truncated ${serialized.length - MAX_JSON_CHARS} chars]`;
  } catch {
    return '[Unable to render JSON payload]';
  }
}

export function JsonViewer({ value }: Readonly<{ value: unknown }>) {
  return (
    <pre className="max-h-96 overflow-auto rounded-2xl bg-slate-950 p-4 text-xs leading-relaxed text-slate-100">
      {safeStringify(value)}
    </pre>
  );
}
