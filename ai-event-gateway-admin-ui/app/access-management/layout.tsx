import type { ReactNode } from 'react';
import { AccessManagementProvider } from '@/components/access-management/AccessManagementProvider';
import { AccessManagementShell } from '@/components/access-management/AccessManagementShell';

export default function AccessManagementLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <AccessManagementProvider><AccessManagementShell>{children}</AccessManagementShell></AccessManagementProvider>;
}
