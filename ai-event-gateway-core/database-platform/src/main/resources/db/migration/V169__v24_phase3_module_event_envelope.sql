-- OpenDispatch v24 Phase 3: queryable canonical async event envelope.
-- Payload JSON remains immutable event-specific evidence; these columns are the cross-event lineage index.
alter table module_outbox_events add column if not exists payload_version varchar(32);
alter table module_outbox_events add column if not exists tenant_id varchar(128);
alter table module_outbox_events add column if not exists root_task_id varchar(128);
alter table module_outbox_events add column if not exists task_id varchar(128);
alter table module_outbox_events add column if not exists correlation_id varchar(160);
alter table module_outbox_events add column if not exists causation_id varchar(160);
alter table module_outbox_events add column if not exists trace_id varchar(128);
alter table module_outbox_events add column if not exists span_id varchar(64);
alter table module_outbox_events add column if not exists actor_type varchar(48);
alter table module_outbox_events add column if not exists actor_id varchar(160);
alter table module_outbox_events add column if not exists lineage_status varchar(64);

update module_outbox_events
set payload_version=coalesce(nullif(payload_json->>'payloadVersion',''),'1'),
    tenant_id=nullif(payload_json->>'tenantId',''),
    root_task_id=coalesce(nullif(payload_json->>'rootTaskId',''),nullif(payload_json->>'taskId','')),
    task_id=nullif(payload_json->>'taskId',''),
    correlation_id=coalesce(nullif(payload_json->>'correlationId',''),event_id),
    causation_id=nullif(payload_json->>'causationId',''),
    trace_id=nullif(payload_json->>'traceId',''),
    span_id=nullif(payload_json->>'spanId',''),
    actor_type=coalesce(nullif(payload_json->>'actorType',''),'SYSTEM'),
    actor_id=coalesce(nullif(payload_json->>'actorId',''),'opendispatch'),
    lineage_status=case when nullif(payload_json->>'correlationId','') is null
                        then 'LEGACY_CORRELATION_FALLBACK' else 'PROPAGATED' end
where correlation_id is null;

alter table module_outbox_events alter column payload_version set default '1';
alter table module_outbox_events alter column correlation_id set not null;
alter table module_outbox_events alter column lineage_status set default 'PROPAGATED';
alter table module_outbox_events alter column lineage_status set not null;

create index if not exists idx_module_outbox_events_correlation
  on module_outbox_events(correlation_id,created_at,event_id);
create index if not exists idx_module_outbox_events_task_lineage
  on module_outbox_events(tenant_id,task_id,created_at,event_id)
  where task_id is not null;
create index if not exists idx_module_outbox_events_causation
  on module_outbox_events(causation_id,created_at,event_id)
  where causation_id is not null;

comment on column module_outbox_events.correlation_id is 'Business journey correlation propagated across async boundaries; event-id fallback is explicitly marked in lineage_status.';
comment on column module_outbox_events.causation_id is 'Direct upstream event/command/callback identifier that caused this module event.';
comment on column module_outbox_events.lineage_status is 'PROPAGATED for canonical lineage; LEGACY_CORRELATION_FALLBACK identifies pre-convergence events.';
