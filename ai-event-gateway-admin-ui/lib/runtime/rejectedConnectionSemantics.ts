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
    title: 'Rejected Connections ',
    description: ' Agent  Gateway/Core authorization policy reject runtime  Disconnect,Agent Healthy Gateway restart  rejected connections.',
    examples: [
      'Error credential / token',
      'unknown Agent or Core profile missing',
      'disabled / revoked / rejected Agent ',
      'duplicate connection policy  session',
      'protocol / tenant / capability policy '
    ],
    nonExamples: [
      'Admin UI  Disconnect',
      'Agent Healthy shutdown or TCP session Close',
      'Gateway restart  runtime session ',
      'Core governance Status Agent '
    ]
  };
}

export function runtimeDisconnectSemantics(): RuntimeDisconnectSemanticsCopy {
  return {
    title: 'Manual Disconnect is runtime Actions, not is rejected connection',
    description: 'Disconnect Actions Core/Netty Closecurrent runtime sessionEvent details Runtime Events or Core security events view; onlyhas Agent  Rejected Connection.',
    destination: 'Runtime Events / Core security events'
  };
}

export function rejectedConnectionMetricSubtitle(): string {
  return 'Handshake denied only; excludes manual disconnect';
}

export function rejectedConnectionsEmptyTitle(): string {
  return 'No data is currently available.';
}

export function rejectedConnectionsEmptyDescription(): string {
  return `${rejectedConnectionSemantics().description}  DisconnectReview the configuration and try again. Runtime Events or Core security events View details`;
}

export function latestRejectedConnectionsEmptyText(): string {
  return 'No Agent  Disconnect Review the configuration and try again. Runtime Events view.';
}

export function manualDisconnectResultNotice(agentId: string, allSessions = false): string {
  const scope = allSessions ? ' observed Gateway sessions' : 'current Netty runtime session';
  return `Agent ${agentId} ${scope} manual disconnect Rejected Connections; Please to Runtime Events or Core security events View details`;
}

export function appendManualDisconnectNotice(message: string | undefined, agentId: string, allSessions = false): string {
  const notice = manualDisconnectResultNotice(agentId, allSessions);
  if (!message || message.trim().length === 0) return notice;
  if (message.includes('Rejected Connections') || message.includes('manual disconnect')) return message;
  return `${message} ${notice}`;
}
