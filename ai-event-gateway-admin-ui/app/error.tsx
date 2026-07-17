'use client';

import { RouteState } from '@/components/common/RouteState';

export default function Error({ error, reset }: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  return (
    <RouteState
      title="管理後台發生錯誤"
      description={error.message || '畫面渲染或資料處理時發生未預期錯誤，請重新整理或返回 Dashboard。'}
      actionLabel="重新載入此頁"
      onAction={reset}
    />
  );
}
