import { Suspense } from 'react';
import { AgentGovernanceConsole } from '@/components/agents/AgentGovernanceConsole';
import { HubQuickLinks } from '@/components/common/HubQuickLinks';
import { LoadingBox } from '@/components/common/LoadingBox';
import { PageHeader } from '@/components/common/PageHeader';

export default function AgentsPage() {
  return (
    <main className="space-y-6">
      <PageHeader
        title="Agent"
        description="建立與核准 Agent、查看 Runtime 連線、維護選填特殊能力，並確認它是否已被派工流程選用。"
      />
      <HubQuickLinks
        title="Agent 標準操作捷徑"
        description="一般管理員只需要 Agent、來源系統、派工流程與 Task。底層 Scope、Profile、Governance 與 Readiness 工具已退出標準流程。"
        links={[
          { href: '/agents/setup', label: '建立第一個 Agent', description: '為空資料庫或新 runtime 建立 Agent。' },
          { href: '/source-systems', label: '查看來源系統', description: '確認企業自行定義的事件來源。' },
          { href: '/dispatch-flows', label: '開啟派工流程', description: '唯一的派工設定入口；在 Flow 內選擇處理 Agent。' },
          { href: '/tasks', label: '查看 Task', description: '追蹤派工結果、失敗原因與人工處置。' },
        ]}
      />
      <Suspense fallback={<LoadingBox label="Loading agents..." />}>
        <AgentGovernanceConsole />
      </Suspense>
    </main>
  );
}
