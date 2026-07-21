import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import './globals.css';
import { AppShell } from '@/components/layout/AppShell';

export const metadata: Metadata = {
  title: 'OpenDispatch 管理中心',
  description: '管理來源系統、派工流程、Agent、Task 與執行狀態'
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="zh-Hant">
      <body>
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
