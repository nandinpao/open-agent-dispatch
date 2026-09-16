import type { ReactNode } from 'react';
import { CanonicalManagementGate } from '@/components/platform-administration/CanonicalManagementGate';

export default function PlatformAdministrationLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <CanonicalManagementGate>{children}</CanonicalManagementGate>;
}
