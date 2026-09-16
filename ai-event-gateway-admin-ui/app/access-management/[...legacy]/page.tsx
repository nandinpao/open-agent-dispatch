import { redirect } from 'next/navigation';
import type { AccessManagementSearchParams } from '@/lib/navigation/canonicalAccessManagementRoute';
import { recordLegacyAccessManagementHit, resolveLegacyAccessManagementRoute } from '@/lib/navigation/legacyAccessManagementRoutes';

export default async function Page({
  params,
  searchParams,
}: Readonly<{
  params: Promise<{ legacy: string[] }>;
  searchParams: Promise<AccessManagementSearchParams>;
}>) {
  const [{ legacy }, query] = await Promise.all([params, searchParams]);
  const resolution = resolveLegacyAccessManagementRoute(legacy, query);
  recordLegacyAccessManagementHit(resolution);
  redirect(resolution.target);
}
