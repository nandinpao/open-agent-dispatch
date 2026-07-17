-- Basic installation verification. This script must fail fast when a required table is missing.
with required_tables(object_name) as (
    values
      ('incidents'),
      ('incident_occurrence_summary'),
      ('event_decisions'),
      ('event_dedup_state'),
      ('tasks'),
      ('agents'),
      ('routing_decisions'),
      ('task_assignments'),
      ('dispatch_requests'),
      ('task_callbacks'),
      ('adapter_actions'),
      ('adapter_executor_audit'),
      ('gateway_nodes'),
      ('module_outbox_events'),
      ('integration_event_outbox')
), table_status as (
    select object_name, to_regclass('public.' || object_name) is not null as exists
    from required_tables
)
select object_name, exists
from table_status
order by object_name;

do $$
declare
    missing text;
begin
    select string_agg(object_name, ', ' order by object_name)
      into missing
    from (
        values
          ('incidents'),
          ('incident_occurrence_summary'),
          ('event_decisions'),
          ('event_dedup_state'),
          ('tasks'),
          ('agents'),
          ('routing_decisions'),
          ('task_assignments'),
          ('dispatch_requests'),
          ('task_callbacks'),
          ('adapter_actions'),
          ('adapter_executor_audit'),
          ('gateway_nodes'),
          ('module_outbox_events'),
          ('integration_event_outbox')
    ) as required_tables(object_name)
    where to_regclass('public.' || object_name) is null;

    if missing is not null then
        raise exception 'ai-event-gateway-core schema is incomplete. Missing required table(s): %', missing;
    end if;
end $$;

-- Key uniqueness/index checks used by reliability hardening and external worker claim.
select indexname
from pg_indexes
where schemaname = 'public'
  and indexname in (
      'ux_incidents_active_fingerprint',
      'ux_tasks_open_incident_type',
      'ux_adapter_actions_idempotency_key',
      'idx_adapter_actions_external_claimable',
      'idx_dispatch_requests_claimable',
      'idx_dispatch_requests_claim_owner',
      'ux_dispatch_requests_open_assignment',
      'idx_module_outbox_dispatch',
      'idx_integration_event_outbox_dispatch'
  )
order by indexname;

-- M8 integration event outbox and M7 module outbox are both mandatory for MYBATIS mode.
select count(*) as integration_event_outbox_count from integration_event_outbox;
select count(*) as module_outbox_events_count from module_outbox_events;

-- P5 dispatch claim/lease columns.
select column_name
from information_schema.columns
where table_schema = 'public'
  and table_name = 'dispatch_requests'
  and column_name in ('claimed_by', 'claim_started_at', 'claim_until')
order by column_name;
