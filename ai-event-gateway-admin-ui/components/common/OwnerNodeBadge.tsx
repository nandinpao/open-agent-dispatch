export function OwnerNodeBadge({
  nodeId,
  label = 'Owner'
}: Readonly<{
  nodeId?: string | null;
  label?: string;
}>) {
  return (
    <span className="inline-flex rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">
      {label}: {nodeId || '-'}
    </span>
  );
}
