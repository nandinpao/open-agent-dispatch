export interface RejectedConnectionSemanticsCopy {
  title: string;
  description: string;
  examples: string[];
  nonExamples: string[];
}

export interface RuntimeDisconnectSemanticsCopy {
  title: string;
  description: string;
  destination: string;
}

export function rejectedConnectionSemantics(): RejectedConnectionSemanticsCopy {
  return {
    title: 'Rejected Connections 只代表連線握手被拒絕',
    description: '此頁只統計 Agent 嘗試連線，但被 Gateway/Core authorization policy 拒絕的 runtime 記錄。手動 Disconnect、Agent 正常下線或 Gateway restart 不會列入 rejected connections。',
    examples: [
      '錯誤 credential / token',
      'unknown Agent 或 Core profile missing',
      'disabled / revoked / rejected Agent 嘗試重新連線',
      'duplicate connection policy 拒絕新的 session',
      'protocol / tenant / capability policy 不允許連線'
    ],
    nonExamples: [
      'Admin UI 手動 Disconnect',
      'Agent 正常 shutdown 或 TCP session 關閉',
      'Gateway restart 導致 runtime session 中斷',
      'Core governance 狀態更新但尚未有 Agent 重新嘗試連線'
    ]
  };
}

export function runtimeDisconnectSemantics(): RuntimeDisconnectSemanticsCopy {
  return {
    title: 'Manual Disconnect 是 runtime 操作，不是 rejected connection',
    description: 'Disconnect 代表操作者要求 Core/Netty 關閉目前 runtime session。這類事件應到 Runtime Events 或 Core security events 查看；只有 Agent 後續重新連線且被授權拒絕，才會產生 Rejected Connection。',
    destination: 'Runtime Events / Core security events'
  };
}

export function rejectedConnectionMetricSubtitle(): string {
  return 'Handshake denied only; excludes manual disconnect';
}

export function rejectedConnectionsEmptyTitle(): string {
  return '目前沒有被拒絕的連線';
}

export function rejectedConnectionsEmptyDescription(): string {
  return `${rejectedConnectionSemantics().description} 若你剛剛按的是 Disconnect，請改到 Runtime Events 或 Core security events 查看斷線紀錄。`;
}

export function latestRejectedConnectionsEmptyText(): string {
  return '目前沒有 Agent 連線被拒絕。手動 Disconnect 不會出現在這裡，請到 Runtime Events 查看。';
}

export function manualDisconnectResultNotice(agentId: string, allSessions = false): string {
  const scope = allSessions ? '所有 observed Gateway sessions' : '目前 Netty runtime session';
  return `Agent ${agentId} 的${scope}已送出手動中斷要求。這是 manual disconnect，不會列入 Rejected Connections；請到 Runtime Events 或 Core security events 查看斷線紀錄。`;
}

export function appendManualDisconnectNotice(message: string | undefined, agentId: string, allSessions = false): string {
  const notice = manualDisconnectResultNotice(agentId, allSessions);
  if (!message || message.trim().length === 0) return notice;
  if (message.includes('Rejected Connections') || message.includes('manual disconnect')) return message;
  return `${message} ${notice}`;
}
