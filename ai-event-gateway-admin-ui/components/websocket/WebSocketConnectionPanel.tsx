'use client';

import { StatusBadge } from '@/components/common/StatusBadge';
import { useAdminWebSocket } from '@/hooks/useAdminWebSocket';
import { getPublicEnv } from '@/lib/constants/env';
import { formatDateTime, formatNumber } from '@/lib/utils/format';

export function WebSocketConnectionPanel() {
  const env = getPublicEnv();
  const { connection, pause, resume, reconnect, clearEvents, lastMetricsAt, nodeMetricsById } = useAdminWebSocket();

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-sm font-medium text-slate-500">Admin WebSocket</div>
          <div className="mt-1 flex items-center gap-3">
            <div className="text-lg font-bold text-slate-950">即時事件流</div>
            <StatusBadge status={connection.status} />
          </div>
          <div className="mt-2 max-w-3xl break-all text-xs text-slate-500">{env.useMock ? '目前使用 Mock 事件流。' : env.nettyRuntimeWsUrl}</div>
          <p className="mt-2 max-w-3xl text-xs leading-5 text-slate-500">此連線代表 Admin UI 到 Netty runtime stream；它是 runtime-plane 即時事件，不是 Core control-plane 權威狀態。Agent online、delivery event、callback relay event 都需要與 Core snapshot 定期 reconcile。</p>
        </div>

        <div className="flex flex-wrap gap-2">
          {connection.paused ? (
            <button type="button" onClick={resume} className="rounded-xl bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700">
              Resume
            </button>
          ) : (
            <button type="button" onClick={pause} className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50">
              Pause
            </button>
          )}
          <button type="button" onClick={clearEvents} className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50">
            Clear Events
          </button>
          {!env.useMock ? (
            <button type="button" onClick={reconnect} className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50">
              Reconnect
            </button>
          ) : null}
        </div>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-3 text-sm md:grid-cols-2 xl:grid-cols-7">
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Connected At</div>
          <div className="mt-1 text-slate-900">{connection.connectedAt ? formatDateTime(connection.connectedAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Last Message</div>
          <div className="mt-1 text-slate-900">{connection.lastMessageAt ? formatDateTime(connection.lastMessageAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Heartbeat / Pong</div>
          <div className="mt-1 text-slate-900">{connection.lastHeartbeatSentAt ? formatDateTime(connection.lastHeartbeatSentAt) : '-'} / {connection.lastPongAt ? formatDateTime(connection.lastPongAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Reconnect</div>
          <div className="mt-1 text-slate-900">#{connection.reconnectAttempts} / {connection.nextReconnectAt ? formatDateTime(connection.nextReconnectAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Last Control</div>
          <div className="mt-1 text-slate-900">{connection.lastControlMessageType ?? '-'}</div>
          <div className="mt-1 text-xs text-slate-500">{connection.lastControlMessageAt ? formatDateTime(connection.lastControlMessageAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Unsupported</div>
          <div className="mt-1 text-slate-900">{connection.unsupportedMessageCount}</div>
          <div className="mt-1 text-xs text-slate-500">{connection.lastUnsupportedMessageAt ? formatDateTime(connection.lastUnsupportedMessageAt) : '-'}</div>
        </div>
        <div className="rounded-xl bg-slate-50 p-3">
          <div className="text-xs font-medium text-slate-500">Last Metrics</div>
          <div className="mt-1 text-slate-900">{lastMetricsAt ? formatDateTime(lastMetricsAt) : '-'}</div>
          <div className="mt-1 text-xs text-slate-500">Nodes {formatNumber(Object.keys(nodeMetricsById).length)}</div>
        </div>
      </div>

      {connection.lastUnsupportedMessageReason ? (
        <details className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
          <summary className="cursor-pointer font-semibold">最近一筆未支援 WebSocket 訊息，已忽略但不影響連線</summary>
          <div className="mt-2 text-xs text-slate-500">{connection.lastUnsupportedMessageReason}</div>
          <pre className="mt-2 overflow-auto rounded-lg bg-white p-3 text-xs text-slate-700">{connection.lastUnsupportedMessageSample}</pre>
        </details>
      ) : null}

      {connection.lastError ? <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-medium text-rose-700">{connection.lastError}</div> : null}
      {connection.paused ? <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm font-medium text-amber-700">事件畫面已暫停，WebSocket 仍維持連線。</div> : null}
    </div>
  );
}
