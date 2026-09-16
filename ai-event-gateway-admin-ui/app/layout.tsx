import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import './globals.css';
import { AppShell } from '@/components/layout/AppShell';

export const metadata: Metadata = {
  title: 'OpenDispatch Admin Console',
  description: 'Manage Source Systems, dispatch configuration, Agents, Tasks and runtime evidence'
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en-US">
      <body>
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
