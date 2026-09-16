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
      title: 'Core Task Task details',
      description: 'Task StatusOperation failed. recovery decision  Core persisted state ',
      authoritative: true
    },
    {
      layer: 'DISPATCH_LEDGER',
      title: 'Dispatch Attempt Ledger Dispatch information',
      description: 'delivery,ACK,RESULT,ERROR,timeout and retry decision mustcanby dispatchRequestId / taskId ',
      authoritative: true
    },
    {
      layer: 'CALLBACK_INBOX',
      title: 'Callback Inbox is callback ',
      description: ' Gateway node  Agent callback  durable  Core inbox, again by Core idempotent processor process.',
      authoritative: true
    },
    {
      layer: 'GATEWAY_DIAGNOSTICS',
      title: 'Gateway Node  runtime diagnostics',
      description: 'Gateway telemetry  live session,delivery relay,callback relay Task completion or callback recovery ',
      authoritative: false
    }
  ];
}

export function callbackTruthSummary(): string {
  return 'Task / callback truth  Core DB,Dispatch Attempt Ledger and durable Callback Inbox Gateway node only is live transport and diagnostics callback recovery ';
}

export function gatewayDiagnosticsDisclaimer(): string {
  return 'this pageonlydisplay gateway node runtime diagnostics cluster  single,single  cluster, or Agent reconnect to other node,Task completion  Core Dispatch Ledger / Callback Inbox ';
}

export function taskAuthorityDisclaimer(): string {
  return 'The authoritative Task status is persisted in Core. Gateway delivery and callback relay are runtime observations; Core records callbacks idempotently through the Callback Inbox.';
}
