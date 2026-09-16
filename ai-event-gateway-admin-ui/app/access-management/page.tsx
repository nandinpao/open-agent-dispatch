import { redirect } from 'next/navigation';
import { LEGACY_ACCESS_MANAGEMENT_SUNSET, recordLegacyAccessManagementHit } from '@/lib/navigation/legacyAccessManagementRoutes';

export default function Page() {
  recordLegacyAccessManagementHit({
    legacyPath: '(root)',
    target: '/admin/tenants',
    known: true,
    sunset: LEGACY_ACCESS_MANAGEMENT_SUNSET,
  });
  redirect('/admin/tenants');
}
