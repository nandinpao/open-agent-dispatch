-- Destructive helper for local/test environments only. Do not run in production.
drop table if exists integration_event_outbox cascade;
drop table if exists module_outbox_events cascade;
drop table if exists adapter_executor_audit cascade;
drop table if exists adapter_actions cascade;
drop table if exists task_callbacks cascade;
drop table if exists dispatch_requests cascade;
drop table if exists routing_decisions cascade;
drop table if exists task_assignments cascade;
drop table if exists agents cascade;
drop table if exists gateway_nodes cascade;
drop table if exists tasks cascade;
drop table if exists dedup_state_snapshots cascade;
drop table if exists event_decisions cascade;
drop table if exists incident_occurrence_summary cascade;
drop table if exists incidents cascade;
