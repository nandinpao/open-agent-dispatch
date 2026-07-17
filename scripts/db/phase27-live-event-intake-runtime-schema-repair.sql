-- Phase 27 live schema repair for CMS event intake and runtime monitoring.
-- Safe for local/dev databases already migrated from the clean V1 baseline.

alter table agents alter column agent_type type varchar(128);
alter table agents alter column region type varchar(128);
alter table agents alter column zone type varchar(128);
alter table agents alter column plugin_version type varchar(255);
alter table agents alter column capability_revision type varchar(255);

-- Widen normalized runtime state columns that receive gateway-reported values.
alter table agent_runtime_capability_profiles alter column plugin_version type varchar(255);
alter table agent_runtime_capability_profiles alter column capability_revision type varchar(255);
alter table agent_runtime_capability_items alter column capability_revision type varchar(255);
alter table agent_runtime_descriptors alter column plugin_version type varchar(255);
alter table agent_runtime_descriptors alter column protocol_version type varchar(255);


alter table task_callbacks add column if not exists callback_type varchar(64);
alter table task_callbacks add column if not exists assignment_id varchar(128);
alter table task_callbacks add column if not exists owner_gateway_node_id varchar(128);
alter table task_callbacks add column if not exists agent_session_id varchar(128);
alter table task_callbacks add column if not exists attempt_no int;
alter table task_callbacks add column if not exists fencing_token varchar(255);
alter table task_callbacks add column if not exists accepted boolean;
alter table task_callbacks add column if not exists ignored_reason varchar(128);
alter table task_callbacks add column if not exists message text;
alter table task_callbacks add column if not exists progress_percent int;
alter table task_callbacks add column if not exists error_code varchar(128);
alter table task_callbacks add column if not exists error_message text;
alter table task_callbacks add column if not exists occurred_at timestamptz;
alter table task_callbacks add column if not exists processed_at timestamptz not null default now();
alter table task_callbacks add column if not exists duplicate boolean not null default false;
alter table task_callbacks add column if not exists idempotency_key varchar(255);
alter table task_callbacks add column if not exists callback_fingerprint varchar(255);
alter table task_callbacks add column if not exists replay_detected boolean not null default false;
alter table task_callbacks add column if not exists previous_task_status varchar(64);
alter table task_callbacks add column if not exists new_task_status varchar(64);
alter table task_callbacks add column if not exists previous_dispatch_status varchar(64);
alter table task_callbacks add column if not exists new_dispatch_status varchar(64);

create index if not exists idx_task_callbacks_task on task_callbacks(task_id, processed_at desc);
create index if not exists idx_task_callbacks_dispatch on task_callbacks(dispatch_request_id, processed_at desc);
create index if not exists idx_task_callbacks_type_time on task_callbacks(callback_type, processed_at desc);
create index if not exists idx_task_callbacks_agent_time on task_callbacks(agent_id, processed_at desc);

alter table event_dedup_state add column if not exists fingerprint varchar(255);
update event_dedup_state
set fingerprint = coalesce(dedup_key, md5(coalesce(tenant_id, '') || ':' || coalesce(source_system, '') || ':' || coalesce(snapshot_json::text, '') || ':' || ctid::text))
where fingerprint is null;
alter table event_dedup_state alter column fingerprint set not null;
create unique index if not exists ux_event_dedup_state_fingerprint on event_dedup_state(fingerprint);

alter table event_dedup_state add column if not exists active_incident_id varchar(128);
alter table event_dedup_state add column if not exists site_id varchar(128);
alter table event_dedup_state add column if not exists plant_id varchar(128);
alter table event_dedup_state add column if not exists object_type varchar(128);
alter table event_dedup_state add column if not exists object_id varchar(255);
alter table event_dedup_state add column if not exists event_type varchar(128);
alter table event_dedup_state add column if not exists error_code varchar(128);
alter table event_dedup_state add column if not exists occurrence_count int not null default 1;
alter table event_dedup_state add column if not exists max_severity varchar(32);
alter table event_dedup_state add column if not exists last_event_id varchar(128);
alter table event_dedup_state add column if not exists last_message text;
alter table event_dedup_state add column if not exists expires_at timestamptz;
alter table event_dedup_state add column if not exists created_at timestamptz not null default now();
alter table event_dedup_state add column if not exists updated_at timestamptz not null default now();

create index if not exists idx_event_dedup_state_tenant_source on event_dedup_state(tenant_id, source_system);
create index if not exists idx_event_dedup_state_active_incident on event_dedup_state(active_incident_id);
create index if not exists idx_event_dedup_state_expires_at on event_dedup_state(expires_at);
