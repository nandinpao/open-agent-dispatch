'use client';

import type { ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AdminRealtimeContext } from '@/hooks/useAdminRealtime';
import { AUTH_SESSION_CHANGED_EVENT, type AuthSessionChangedDetail } from '@/lib/auth/session';
import { getPublicEnv } from '@/lib/constants/env';
import { mockAdminWebSocketEvents } from '@/lib/mock/admin';
import type { AdminWebSocketEvent, ClusterNodeMetrics, GatewayMetrics } from '@/lib/types/admin';
import type { AdminRealtimeContextValue, AdminToastMessage, WebSocketConnectionState } from '@/lib/types/realtime';
import { parseAdminWebSocketMessage } from '@/lib/websocket/message';
import { extractMetricsPayload, isRecord, normalizeClusterNodeMetrics, normalizeGatewayMetrics, pickValue, readMetricsNodeId } from '@/lib/utils/metrics';

const initialConnection: WebSocketConnectionState = {
  status: 'CONNECTING',
  connectedAt: null,
  disconnectedAt: null,
  lastMessageAt: null,
  lastHeartbeatSentAt: null,
  lastPongAt: null,
  lastControlMessageAt: null,
  lastControlMessageType: null,
  unsupportedMessageCount: 0,
  lastUnsupportedMessageAt: null,
  lastUnsupportedMessageReason: null,
  lastUnsupportedMessageSample: null,
  reconnectAttempts: 0,
  nextReconnectAt: null,
  lastError: null,
  paused: false
};


function safeMessageSample(value: unknown): string {
  try {
    const serialized = typeof value === 'string' ? value : JSON.stringify(value);
    return serialized.length > 500 ? `${serialized.slice(0, 500)}...` : serialized;
  } catch {
    return '[unserializable websocket message]';
  }
}

function makeToast(level: AdminToastMessage['level'], title: string, message?: string): AdminToastMessage {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    level,
    title,
    message,
    timestamp: new Date().toISOString()
  };
}


function isMetricsEvent(event: AdminWebSocketEvent): boolean {
  return event.eventType === 'NODE_METRICS_UPDATED'
    || event.eventType === 'CLUSTER_METRICS_UPDATED'
    || event.eventType === 'GATEWAY_METRICS_UPDATED'
    || event.eventType === 'METRICS_UPDATED';
}

function pickNodeMetricsPayload(raw: unknown): unknown {
  if (!isRecord(raw)) return raw;
  if (isRecord(raw.metrics)) return raw.metrics;
  return raw;
}

function readPayloadNodeId(raw: unknown): string | undefined {
  if (!isRecord(raw)) return undefined;
  const value = pickValue(raw, ['nodeId', 'gatewayNodeId', 'node', 'id', 'name']);
  return value === undefined || value === null ? undefined : String(value);
}

function toastFromEvent(event: AdminWebSocketEvent): AdminToastMessage | null {
  if (event.eventType === 'NODE_HEARTBEAT_TIMEOUT') {
    return makeToast('warning', 'Cluster 節點心跳逾時', event.message ?? event.nodeId);
  }
  if (event.eventType === 'NODE_LEFT') {
    return makeToast('warning', 'Cluster 節點離線', event.message ?? event.nodeId);
  }
  if (event.eventType === 'AGENT_DISCONNECTED') {
    return makeToast('warning', 'Agent 已斷線', event.message ?? event.agentId);
  }
  if (event.eventType === 'TASK_FAILED') {
    return makeToast('error', '任務處理失敗', event.message ?? event.taskId);
  }
  if (event.eventType === 'DELIVERY_FAILED') {
    return makeToast('error', 'Command delivery 失敗', event.message ?? event.taskId);
  }
  if (event.eventType === 'CALLBACK_RELAY_FAILED') {
    return makeToast('error', 'Callback relay 失敗', event.message ?? event.taskId);
  }
  if (event.eventType === 'SECURITY_CONNECTION_REJECTED') {
    return makeToast('warning', '未授權 Agent 連線已拒絕', event.message ?? event.agentId);
  }
  if (event.eventType === 'SECURITY_CREDENTIAL_REVOKED_ATTEMPT') {
    return makeToast('error', '撤銷憑證嘗試重連', event.message ?? event.agentId);
  }
  if (event.eventType === 'TASK_CANCELLED') {
    return makeToast('warning', '任務已取消', event.message ?? event.taskId);
  }
  if (event.eventType === 'NODE_JOINED') {
    return makeToast('success', 'Cluster 節點上線', event.nodeId);
  }
  if (event.eventType === 'AGENT_CONNECTED') {
    return makeToast('success', 'Agent 已連線', event.agentId);
  }
  if (event.eventType === 'AGENT_AUTHORIZED') {
    return makeToast('success', 'Agent 已通過 Core 授權', event.agentId);
  }
  if (event.eventType === 'AGENT_AUTHORIZATION_DENIED') {
    return makeToast('warning', 'Agent Core 授權拒絕', event.message ?? event.agentId);
  }
  return null;
}

export function AdminRealtimeProvider({ children }: Readonly<{ children: ReactNode }>) {
  const env = getPublicEnv();
  const [events, setEvents] = useState<AdminWebSocketEvent[]>([]);
  const [toasts, setToasts] = useState<AdminToastMessage[]>([]);
  const [connection, setConnection] = useState<WebSocketConnectionState>(initialConnection);
  const [gatewayMetrics, setGatewayMetrics] = useState<GatewayMetrics | null>(null);
  const [nodeMetricsById, setNodeMetricsById] = useState<Record<string, ClusterNodeMetrics>>({});
  const [lastMetricsAt, setLastMetricsAt] = useState<string | null>(null);
  const sourceRef = useRef<EventSource | null>(null);
  const pausedRef = useRef(false);

  const addToast = useCallback((toast: AdminToastMessage) => setToasts((prev) => [toast, ...prev].slice(0, 5)), []);
  const dismissToast = useCallback((id: string) => setToasts((prev) => prev.filter((toast) => toast.id !== id)), []);

  const applyMetricsEvent = useCallback((event: AdminWebSocketEvent) => {
    if (!isMetricsEvent(event)) return;
    const payload = extractMetricsPayload(event.payload ?? event);
    const timestamp = event.timestamp ?? new Date().toISOString();
    setLastMetricsAt(timestamp);
    if (event.eventType === 'GATEWAY_METRICS_UPDATED') {
      setGatewayMetrics(normalizeGatewayMetrics({ ...(isRecord(payload) ? payload : {}), nodeId: event.nodeId, timestamp })); return;
    }
    if (event.eventType === 'CLUSTER_METRICS_UPDATED' && isRecord(payload)) {
      const rawNodes = Array.isArray(payload.nodes) ? payload.nodes : Array.isArray(payload.clusterNodes) ? payload.clusterNodes : [];
      const next: Record<string, ClusterNodeMetrics> = {};
      for (const rawNode of rawNodes) {
        const nodeId = readPayloadNodeId(rawNode); if (!nodeId) continue;
        const metricsPayload = pickNodeMetricsPayload(rawNode);
        next[nodeId] = normalizeClusterNodeMetrics({ ...(isRecord(metricsPayload) ? metricsPayload : {}), timestamp });
      }
      if (Object.keys(next).length) setNodeMetricsById((prev) => ({ ...prev, ...next }));
      if (payload.gatewayMetrics || payload.metrics) {
        const gateway = isRecord(payload.gatewayMetrics) ? payload.gatewayMetrics : isRecord(payload.metrics) ? payload.metrics : {};
        setGatewayMetrics(normalizeGatewayMetrics({ ...gateway, timestamp }));
      }
      return;
    }
    const nodeId = readMetricsNodeId(event) ?? (isRecord(payload) ? readPayloadNodeId(payload) : undefined);
    if (nodeId) {
      const metrics = normalizeClusterNodeMetrics({ ...(isRecord(payload) ? payload : {}), timestamp });
      setNodeMetricsById((prev) => ({ ...prev, [nodeId]: metrics }));
    } else {
      setGatewayMetrics(normalizeGatewayMetrics({ ...(isRecord(payload) ? payload : {}), timestamp }));
    }
  }, []);

  const addEvent = useCallback((event: AdminWebSocketEvent) => {
    setConnection((prev) => ({ ...prev, lastMessageAt: event.timestamp }));
    applyMetricsEvent(event);
    if (!pausedRef.current) setEvents((prev) => [event, ...prev].slice(0, env.wsMaxEvents));
    const toast = toastFromEvent(event); if (toast) addToast(toast);
  }, [addToast, applyMetricsEvent, env.wsMaxEvents]);

  const disconnect = useCallback(() => { sourceRef.current?.close(); sourceRef.current = null; }, []);
  const connect = useCallback(() => {
    disconnect();
    if (env.useMock) return;
    setConnection((prev) => ({ ...prev, status: 'CONNECTING', lastError: null }));
    const source = new EventSource('/api/realtime/events', { withCredentials: true });
    sourceRef.current = source;
    source.onopen = () => setConnection((prev) => ({ ...prev, status: 'OPEN', connectedAt: new Date().toISOString(), disconnectedAt: null, lastError: null, reconnectAttempts: 0 }));
    source.addEventListener('runtime', (message) => {
      const parsed = parseAdminWebSocketMessage(String((message as MessageEvent).data));
      if (parsed.kind === 'event') addEvent(parsed.event);
      else if (parsed.kind === 'unknown') setConnection((prev) => ({ ...prev, unsupportedMessageCount: prev.unsupportedMessageCount + 1, lastUnsupportedMessageAt: parsed.timestamp, lastUnsupportedMessageReason: parsed.reason, lastUnsupportedMessageSample: safeMessageSample(parsed.raw) }));
    });
    source.addEventListener('error', () => setConnection((prev) => ({ ...prev, status: 'RECONNECTING', lastError: 'Authenticated runtime event relay is reconnecting.', reconnectAttempts: prev.reconnectAttempts + 1, disconnectedAt: new Date().toISOString() })));
  }, [addEvent, disconnect, env.useMock]);

  useEffect(() => {
    if (env.useMock) {
      setConnection((prev) => ({ ...prev, status: 'MOCK', connectedAt: new Date().toISOString(), lastError: null }));
      const timer = window.setInterval(() => {
        const event = mockAdminWebSocketEvents[Math.floor(Math.random() * mockAdminWebSocketEvents.length)];
        addEvent({ ...event, timestamp: new Date().toISOString() });
      }, 3500);
      return () => window.clearInterval(timer);
    }
    connect(); return disconnect;
  }, [addEvent, connect, disconnect, env.useMock]);

  useEffect(() => {
    const handle = (event: Event) => {
      const detail = (event as CustomEvent<AuthSessionChangedDetail>).detail;
      if (detail?.sessionChanged || detail?.tenantChanged) connect();
    };
    window.addEventListener(AUTH_SESSION_CHANGED_EVENT, handle);
    return () => window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, handle);
  }, [connect]);

  const clearEvents = useCallback(() => setEvents([]), []);
  const pause = useCallback(() => { pausedRef.current = true; setConnection((prev) => ({ ...prev, paused: true })); }, []);
  const resume = useCallback(() => { pausedRef.current = false; setConnection((prev) => ({ ...prev, paused: false })); }, []);
  const reconnect = useCallback(() => connect(), [connect]);
  const sendJson = useCallback(() => false, []);

  const value: AdminRealtimeContextValue = useMemo(() => ({
    events, connection, toasts, clearEvents, pause, resume, reconnect, dismissToast,
    gatewayMetrics, nodeMetricsById, lastMetricsAt, sendJson
  }), [clearEvents, connection, dismissToast, events, gatewayMetrics, lastMetricsAt, nodeMetricsById, pause, reconnect, resume, sendJson, toasts]);

  return <AdminRealtimeContext.Provider value={value}>{children}</AdminRealtimeContext.Provider>;
}
