export function PageHeader({
  title,
  description
}: Readonly<{
  title: string;
  description: string;
}>) {
  return (
    <div className="mb-6">
      <h1 className="text-2xl font-bold text-slate-950">{title}</h1>
      <p className="mt-2 text-sm text-slate-600">{description}</p>
    </div>
  );
}
