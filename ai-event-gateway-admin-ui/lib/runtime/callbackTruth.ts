export type CallbackTruthLayer = 'CORE_TASK' | 'DISPATCH_LEDGER' | 'CALLBACK_INBOX' | 'GATEWAY_DIAGNOSTICS';

export interface CallbackTruthPrinciple {
  layer: CallbackTruthLayer;
  title: string;
  description: string;
  authoritative: boolean;
}

export function callbackTruthPrinciples(): CallbackTruthPrinciple[] {
  return [
    {
      layer: 'CORE_TASK',
      title: 'Core Task 是任務真相',
      description: 'Task 狀態、指派、完成、失敗與 recovery decision 應以 Core persisted state 為準。',
      authoritative: true
    },
    {
      layer: 'DISPATCH_LEDGER',
      title: 'Dispatch Attempt Ledger 是派工真相',
      description: 'delivery、ACK、RESULT、ERROR、timeout 與 retry decision 必須可由 dispatchRequestId / taskId 查詢與恢復。',
      authoritative: true
    },
    {
      layer: 'CALLBACK_INBOX',
      title: 'Callback Inbox 是 callback 真相',
      description: '任一 Gateway node 收到 Agent callback 後都應 durable 寫入 Core inbox，再由 Core idempotent processor 處理。',
      authoritative: true
    },
    {
      layer: 'GATEWAY_DIAGNOSTICS',
      title: 'Gateway Node 只做 runtime diagnostics',
      description: 'Gateway telemetry 可用來診斷 live session、delivery relay、callback relay，但不能作為 Task completion 或 callback recovery 的唯一依據。',
      authoritative: false
    }
  ];
}

export function callbackTruthSummary(): string {
  return 'Task / callback truth 必須以 Core DB、Dispatch Attempt Ledger 與 durable Callback Inbox 為準；Gateway node 僅是 live transport 與 diagnostics，不可作為 callback recovery 的唯一依據。';
}

export function gatewayDiagnosticsDisclaimer(): string {
  return '此頁只顯示 gateway node runtime diagnostics。若服務從 cluster 轉 single、single 轉 cluster，或 Agent reconnect 到其他 node，Task completion 仍應透過 Core Dispatch Ledger / Callback Inbox 查詢與恢復。';
}

export function taskAuthorityDisclaimer(): string {
  return '此 Task 的權威狀態在 Core persisted state；Gateway delivery / callback relay 僅為 runtime observation。若原 Gateway node 中斷，Agent 可透過任一可用 Gateway 重送 callback，由 Core Callback Inbox idempotently 完成處理。';
}
