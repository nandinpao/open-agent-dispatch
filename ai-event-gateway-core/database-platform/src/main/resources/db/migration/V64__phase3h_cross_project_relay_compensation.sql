-- Phase 3H: provider-neutral cross-project relay topology and compensation.
create table if not exists integration_canonical_issue_relations (
 tenant_id varchar(64) not null, relation_id varchar(128) not null, relation_type varchar(64) not null,
 parent_task_id varchar(128) not null, child_task_id varchar(128) not null,
 source_task_issue_link_id varchar(128), target_task_issue_link_id varchar(128), status varchar(32) not null,
 row_version bigint not null default 1, created_at timestamptz not null, updated_at timestamptz not null,
 correlation_id varchar(128), primary key(tenant_id,relation_id),
 constraint ck_canonical_relation_status_p3h check(status in ('ACTIVE','COMPENSATING','COMPENSATED','SUPERSEDED')),
 constraint uq_canonical_relation_tasks_p3h unique(tenant_id,parent_task_id,child_task_id,relation_type)
);
create table if not exists integration_relay_topologies (
 tenant_id varchar(64) not null, topology_id varchar(128) not null, canonical_relation_id varchar(128) not null,
 topology_type varchar(32) not null, status varchar(32) not null, edge_count integer not null default 0,
 synced_edge_count integer not null default 0, failed_edge_count integer not null default 0,
 decision_edge_count integer not null default 0, row_version bigint not null default 1,
 created_at timestamptz not null, updated_at timestamptz not null, correlation_id varchar(128),
 primary key(tenant_id,topology_id),
 constraint fk_relay_topology_relation_p3h foreign key(tenant_id,canonical_relation_id) references integration_canonical_issue_relations(tenant_id,relation_id),
 constraint ck_relay_topology_type_p3h check(topology_type in ('ONE_TO_ONE','ONE_TO_MANY','MANY_TO_ONE','MULTI_PROVIDER')),
 constraint ck_relay_topology_status_p3h check(status in ('READY','PARTIAL','BROKEN','DECISION_REQUIRED','COMPENSATING','COMPENSATED')),
 constraint ck_relay_topology_counts_p3h check(edge_count>=0 and synced_edge_count>=0 and failed_edge_count>=0 and decision_edge_count>=0)
);
create table if not exists integration_relay_edges (
 tenant_id varchar(64) not null, edge_id varchar(128) not null, topology_id varchar(128) not null,
 connection_id varchar(128) not null, project_mapping_id varchar(128), provider_type varchar(32) not null,
 external_project_id varchar(255), source_external_issue_id varchar(255) not null, target_external_issue_id varchar(255),
 edge_type varchar(48) not null, relation_type varchar(64) not null, source_marker varchar(255) not null,
 status varchar(32) not null, attempt_count integer not null default 0, next_attempt_at timestamptz,
 provider_object_id varchar(255), last_error_code varchar(128), last_error_message text,
 row_version bigint not null default 1, created_at timestamptz not null, updated_at timestamptz not null,
 correlation_id varchar(128), primary key(tenant_id,edge_id),
 constraint fk_relay_edge_topology_p3h foreign key(tenant_id,topology_id) references integration_relay_topologies(tenant_id,topology_id),
 constraint fk_relay_edge_connection_p3h foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 constraint ck_relay_edge_type_p3h check(edge_type in ('SOURCE_BACKLINK','TARGET_BACKLINK','PROVIDER_NATIVE_RELATION','COMPENSATION_COMMENT','COMPENSATION_TRANSITION')),
 constraint ck_relay_edge_status_p3h check(status in ('REQUESTED','READY','IN_PROGRESS','SYNCED','RETRY_WAITING','DEAD_LETTER','FAILED_PERMANENT','SUPERSEDED','COMPENSATED')),
 constraint ck_relay_edge_attempt_p3h check(attempt_count>=0), constraint uq_relay_edge_marker_p3h unique(tenant_id,connection_id,source_marker)
);
create table if not exists integration_relay_compensations (
 tenant_id varchar(64) not null, compensation_id varchar(128) not null, topology_id varchar(128) not null,
 canonical_relation_id varchar(128) not null, reason_code varchar(128) not null, requested_by varchar(128) not null,
 status varchar(32) not null, total_edges integer not null default 0, compensated_edges integer not null default 0,
 failed_edges integer not null default 0, row_version bigint not null default 1,
 created_at timestamptz not null, updated_at timestamptz not null, correlation_id varchar(128),
 primary key(tenant_id,compensation_id),
 constraint fk_relay_comp_topology_p3h foreign key(tenant_id,topology_id) references integration_relay_topologies(tenant_id,topology_id),
 constraint fk_relay_comp_relation_p3h foreign key(tenant_id,canonical_relation_id) references integration_canonical_issue_relations(tenant_id,relation_id),
 constraint ck_relay_comp_status_p3h check(status in ('REQUESTED','IN_PROGRESS','PARTIAL','COMPLETED','FAILED_RETRYABLE','FAILED_PERMANENT','DECISION_REQUIRED')),
 constraint ck_relay_comp_counts_p3h check(total_edges>=0 and compensated_edges>=0 and failed_edges>=0)
);
create table if not exists integration_relay_events (
 tenant_id varchar(64) not null, event_id varchar(128) not null, aggregate_type varchar(48) not null,
 aggregate_id varchar(128) not null, event_type varchar(64) not null, actor_id varchar(128), reason_code varchar(128) not null,
 metadata_json jsonb not null default '{}'::jsonb, previous_event_hash varchar(64), event_hash varchar(64) not null,
 occurred_at timestamptz not null, correlation_id varchar(128), primary key(tenant_id,event_id),
 constraint uq_relay_event_hash_p3h unique(tenant_id,event_hash)
);
create index if not exists idx_relay_topology_status_p3h on integration_relay_topologies(tenant_id,status,updated_at desc);
create index if not exists idx_relay_edge_due_p3h on integration_relay_edges(tenant_id,status,next_attempt_at,updated_at) where status in ('READY','RETRY_WAITING');
create index if not exists idx_relay_edge_topology_p3h on integration_relay_edges(tenant_id,topology_id,status);
create index if not exists idx_relay_comp_topology_p3h on integration_relay_compensations(tenant_id,topology_id,status,updated_at desc);
create index if not exists idx_relay_events_aggregate_p3h on integration_relay_events(tenant_id,aggregate_type,aggregate_id,occurred_at desc);
create or replace function phase3h_relay_event_append_only_guard() returns trigger language plpgsql as $$ begin raise exception 'Phase 3H relay events are append-only'; end $$;
drop trigger if exists trg_relay_events_append_only_p3h on integration_relay_events;
create trigger trg_relay_events_append_only_p3h before update or delete on integration_relay_events for each row execute function phase3h_relay_event_append_only_guard();
create or replace function phase3h_relay_identity_guard() returns trigger language plpgsql as $$ begin if old.tenant_id<>new.tenant_id or old.edge_id<>new.edge_id or old.topology_id<>new.topology_id or old.connection_id<>new.connection_id or old.source_marker<>new.source_marker then raise exception 'Phase 3H relay edge identity is immutable'; end if; return new; end $$;
drop trigger if exists trg_relay_edge_identity_p3h on integration_relay_edges;
create trigger trg_relay_edge_identity_p3h before update on integration_relay_edges for each row execute function phase3h_relay_identity_guard();
