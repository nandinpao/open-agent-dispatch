'use client';

import type { ReactNode } from 'react';
import { RightDrawer } from '@/components/ui/RightDrawer';
import { useI18n } from '@/hooks/useI18n';

export function AgentPoolContextualDrawer({
  open,
  title,
  description,
  version,
  onClose,
  children,
  footer,
}: Readonly<{
  open: boolean;
  title: string;
  description: string;
  version?: string;
  onClose: () => void;
  children: ReactNode;
  footer: ReactNode;
}>) {
  const { t } = useI18n();

  return (
    <RightDrawer
      open={open}
      onClose={onClose}
      title={title}
      description={<><span>{description}</span>{version ? <span className="mt-1 block font-mono text-xs text-slate-500">{version}</span> : null}</>}
      widthClassName="max-w-5xl"
      footer={footer}
    >
      <div className="mb-5 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-950">
        {t('pool.drawer.notice')}
      </div>
      {children}
    </RightDrawer>
  );
}
