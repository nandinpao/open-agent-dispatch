-- V38-3 Route B: Task Issue policy may request a canonical ISSUE_TRACKING AdapterAction.
-- Legacy projection/outbox columns remain historical evidence only and are not execution authority.

alter table issue_policy_decisions
    add column if not exists issue_operation varchar(64),
    add column if not exists adapter_action_id varchar(160),
    add column if not exists action_idempotency_key varchar(160),
    add column if not exists terminal_generation bigint;

alter table issue_policy_decisions drop constraint if exists ck_issue_policy_automation_status;
alter table issue_policy_decisions add constraint ck_issue_policy_automation_status
    check (automation_status in ('NOT_REQUIRED','WAITING_MANUAL_DECISION','BINDING_BLOCKED','INTENT_READY','ACTION_REQUESTED','MATERIALIZED','FAILED'));

alter table issue_policy_decisions drop constraint if exists ck_issue_policy_operation;
alter table issue_policy_decisions add constraint ck_issue_policy_operation
    check (issue_operation is null or issue_operation in ('CREATE_ISSUE','UPDATE_ISSUE','ADD_COMMENT','TRANSITION_ISSUE','READ_ISSUE'));

alter table issue_policy_decisions drop constraint if exists ck_issue_policy_terminal_generation;
alter table issue_policy_decisions add constraint ck_issue_policy_terminal_generation
    check (terminal_generation is null or terminal_generation >= 1);

create unique index if not exists uq_issue_policy_decision_adapter_action
    on issue_policy_decisions(tenant_id, adapter_action_id)
    where adapter_action_id is not null;

create unique index if not exists uq_issue_policy_decision_action_idempotency
    on issue_policy_decisions(tenant_id, action_idempotency_key)
    where action_idempotency_key is not null;

create index if not exists idx_issue_policy_decision_terminal_generation
    on issue_policy_decisions(tenant_id, task_id, terminal_generation desc)
    where terminal_generation is not null;

comment on column issue_policy_decisions.issue_operation is
    'Route-B provider operation vocabulary. This is intent/evidence, not an OpenDispatch provider permission.';
comment on column issue_policy_decisions.adapter_action_id is
    'Canonical ISSUE_TRACKING AdapterAction requested for this Task Issue policy decision.';
comment on column issue_policy_decisions.action_idempotency_key is
    'Stable Task/terminal-generation/policy/operation/mapping idempotency key for Route-B action creation.';
comment on column issue_policy_decisions.terminal_generation is
    'Stable canonical Task finalization generation used by Route-B idempotency.';
