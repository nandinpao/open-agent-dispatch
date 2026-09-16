export const A2A_CHAIN_SECTIONS = [
  "Request Summary",
  "Policy Decision",
  "Child Task",
  "Agent Assignment",
  "Result",
  "State History",
  "Technical Details",
] as const;

export const A2A_REQUEST_STATUS_LABELS: Record<string, string> = {
  REQUESTED: "Requested",
  VALIDATING: "Validating",
  REJECTED: "Rejected",
  WAITING_APPROVAL: "Waiting for Approval",
  APPROVED: "Approved",
  CHILD_TASK_CREATED: "Child Task Created",
  CANCEL_REQUESTED: "Cancellation Requested",
  CANCELLED_CONFIRMED: "Cancellation Confirmed",
  CANCELLED_UNCONFIRMED: "Cancellation Unconfirmed",
  WAIT_HUMAN: "Waiting for Human Decision",
  DISPATCHING: "Dispatching",
  RUNNING: "Running",
  COMPLETED: "Completed",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
};

export const A2A_REASON_MESSAGES: Record<string, string> = {
  A2A_DIRECTION_NOT_ALLOWED: "No active directional A2A policy allows this request.",
  A2A_TASK_TYPE_NOT_ALLOWED: "The selected A2A policy does not allow this Task Type.",
  A2A_SERVICE_CODE_NOT_ALLOWED: "The selected A2A policy does not allow this Service Code.",
  A2A_CAPABILITY_NOT_ALLOWED: "The requested Capability metadata is not allowed by the A2A policy.",
  A2A_SENSITIVITY_EXCEEDED: "The request exceeds the maximum sensitivity allowed by the A2A policy.",
  A2A_HOP_LIMIT_EXCEEDED: "The maximum hop count has been reached. No Child Task was created.",
  A2A_CYCLE_DETECTED: "This request would create a repeated A2A processing cycle.",
  A2A_RATE_LIMIT_EXCEEDED: "The A2A policy rate limit has been reached.",
  REQUESTING_AGENT_NOT_CURRENT_ASSIGNEE: "The requesting Agent is not the current Assigned Agent for this Task.",
  TARGET_POOL_NOT_FOUND: "The target Agent Pool is missing or inactive.",
  A2A_IDEMPOTENCY_CONFLICT: "This Idempotency-Key was used with different request content.",
  A2A_IDEMPOTENCY_IN_PROGRESS: "An A2A request with this Idempotency-Key is still being created.",
};

export const A2A_APPROVAL_MODE_LABELS: Record<string, string> = {
  NONE: "No Approval",
  OPERATOR: "Operator Approval",
  SECURITY: "Security Approval",
  DUAL_APPROVAL: "Two Distinct Approvers",
};

export const A2A_APPROVAL_MESSAGES = {
  firstRecorded: "One of two required approvals has been recorded. A different approver is still required.",
  completed: "The required approvals were recorded. OpenDispatch created the Child Task.",
} as const;

export const A2A_CHAIN_ACTIONS = {
  request: "Request A2A Work",
  approve: "Approve Request",
  recordFirstApproval: "Record First Approval",
  reject: "Reject Request",
  cancel: "Cancel Request",
  openChildTask: "Open Child Task",
  viewPolicy: "View Policy",
  viewStateHistory: "View State History",
} as const;
