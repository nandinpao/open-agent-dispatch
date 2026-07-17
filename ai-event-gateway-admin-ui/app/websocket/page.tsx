import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { DataScopeNotice } from '@/components/common/DataScopeNotice';
import { PageHeader } from '@/components/common/PageHeader';
import { WebSocketEventStream } from '@/components/websocket/WebSocketEventStream';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function WebSocketPage() {
  return (
    <main className="space-y-4">
      <PageHeader title="Runtime Event Center" description="查看 Netty runtime stream 的 Agent 授權、delivery、callback relay、security 與 cluster runtime 事件；這是 runtime-plane 即時資訊，不是 Core 權威狀態。" />
      <AdminUiModeNotice requiredMode="developer" title="Developer tools area" description="This page exposes runtime diagnostics, raw events, fixtures, or compatibility tools. It is hidden from Basic Mode and Advanced Mode navigation by default." />
      <InformationArchitectureGuide activeLayer="runtime" compact />
      <DataScopeNotice kind="realtime" scope="LOCAL" />
      <WebSocketEventStream />
    </main>
  );
}
