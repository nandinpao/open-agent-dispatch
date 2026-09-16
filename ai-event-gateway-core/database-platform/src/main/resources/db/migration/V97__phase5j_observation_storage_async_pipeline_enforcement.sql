-- Phase 5J enforcement: fail-closed async ingestion, idempotency, backpressure,
-- retention/archive execution, RLS and append-only runtime evidence.

alter table permission_shadow_pipeline_settings add constraint ck_phase5j_pipeline_singleton check(singleton_id='ACTIVE');
alter table permission_shadow_pipeline_settings add constraint ck_phase5j_pipeline_limits check(queue_capacity between 1000 and 10000000 and batch_size between 1 and 5000 and maximum_attempts between 1 and 32 and lease_seconds between 5 and 600 and retry_base_seconds between 1 and 300 and partition_days_ahead between 1 and 31 and dead_letter_retention_days between 7 and 3650 and metrics_retention_days between 30 and 3650 and receipt_retention_days between 30 and 3650 and archive_purge_days between 1 and 3650);
alter table permission_shadow_observation_ingress add constraint ck_phase5j_ingress_status check(status in('PENDING','PROCESSING'));
alter table permission_shadow_observation_ingress add constraint ck_phase5j_ingress_priority check(priority between 0 and 1000);
alter table permission_shadow_observation_ingress add constraint ck_phase5j_ingress_attempts check(attempts between 0 and 32);
alter table permission_shadow_observation_ingress add constraint ck_phase5j_ingress_retention check(match_retention_days between 1 and 3650 and mismatch_retention_days between 1 and 3650);
alter table permission_shadow_observation_receipts add constraint ck_phase5j_receipt_tier check(storage_tier in('METRICS_ONLY','QUEUED','SAMPLED_MATCH','DURABLE_MISMATCH','DURABLE_BYPASS','DROPPED_BACKPRESSURE','DEAD_LETTER'));
alter table permission_shadow_partition_archives add constraint ck_phase5j_archive_status check(status in('ARCHIVED','PURGED'));
alter table permission_shadow_retention_runs add constraint ck_phase5j_retention_status check(status in('RUNNING','COMPLETED','FAILED'));
alter table permission_phase5_runtime_certification_evidence add constraint ck_phase5j_runtime_status check(status in('CERTIFIED','FAILED','INCOMPLETE'));

create or replace function phase5j_reject_append_only_mutation() returns trigger language plpgsql as $$begin raise exception 'PHASE5J_EVIDENCE_APPEND_ONLY' using errcode='42501';end$$;
create or replace function phase5j_receipt_retention_guard() returns trigger language plpgsql as $$begin
 if tg_op='DELETE' and current_setting('app.phase5j_retention',true)='true' then return old;end if;
 raise exception 'PHASE5J_EVIDENCE_APPEND_ONLY' using errcode='42501';
end$$;
create trigger trg_phase5j_receipt_immutable before update or delete on permission_shadow_observation_receipts for each row execute function phase5j_receipt_retention_guard();
create trigger trg_phase5j_durable_immutable before update or delete on permission_shadow_durable_mismatches for each row execute function phase5j_reject_append_only_mutation();
create trigger trg_phase5j_sample_immutable before update or delete on permission_shadow_sampled_matches for each row execute function phase5j_reject_append_only_mutation();
create trigger trg_phase5j_dead_letter_immutable before update or delete on permission_shadow_observation_dead_letters for each row execute function phase5j_reject_append_only_mutation();
create trigger trg_phase5j_pipeline_event_immutable before update or delete on permission_shadow_pipeline_events for each row execute function phase5j_reject_append_only_mutation();
create or replace function phase5j_retention_run_guard() returns trigger language plpgsql as $$begin
 if tg_op='UPDATE' and old.status='RUNNING' and new.status in('COMPLETED','FAILED') and new.run_id=old.run_id and new.actor_id=old.actor_id and new.audit_reason=old.audit_reason then return new;end if;
 raise exception 'PHASE5J_RETENTION_EVIDENCE_IMMUTABLE' using errcode='42501';
end$$;
create trigger trg_phase5j_retention_run_immutable before update or delete on permission_shadow_retention_runs for each row execute function phase5j_retention_run_guard();
create trigger trg_phase5j_runtime_evidence_immutable before update or delete on permission_phase5_runtime_certification_evidence for each row execute function phase5j_reject_append_only_mutation();

create or replace function phase5j_upsert_shadow_metric(
 p_tenant varchar,p_domain varchar,p_entry varchar,p_permission varchar,p_category varchar,p_severity varchar,p_risk varchar,p_mode varchar,
 p_observations bigint,p_dropped bigint,p_retries bigint,p_dead bigint,p_queue_delay bigint,p_processing bigint,p_at timestamptz)
returns void language sql security definer set search_path=public,pg_temp as $$
 insert into permission_shadow_observation_metrics(bucket_start,tenant_id,domain_code,entry_point_id,permission_code,mismatch_category,severity,risk_lane,protection_mode,observation_count,dropped_count,retry_count,dead_letter_count,queue_delay_ms_total,queue_delay_ms_max,processing_ms_total,processing_ms_max,updated_at)
 values(date_trunc('minute',p_at),p_tenant,p_domain,coalesce(p_entry,''),p_permission,p_category,p_severity,p_risk,p_mode,p_observations,p_dropped,p_retries,p_dead,greatest(p_queue_delay,0),greatest(p_queue_delay,0),greatest(p_processing,0),greatest(p_processing,0),now())
 on conflict(bucket_start,tenant_id,domain_code,entry_point_id,permission_code,mismatch_category,severity,risk_lane,protection_mode) do update set
 observation_count=permission_shadow_observation_metrics.observation_count+excluded.observation_count,
 dropped_count=permission_shadow_observation_metrics.dropped_count+excluded.dropped_count,
 retry_count=permission_shadow_observation_metrics.retry_count+excluded.retry_count,
 dead_letter_count=permission_shadow_observation_metrics.dead_letter_count+excluded.dead_letter_count,
 queue_delay_ms_total=permission_shadow_observation_metrics.queue_delay_ms_total+excluded.queue_delay_ms_total,
 queue_delay_ms_max=greatest(permission_shadow_observation_metrics.queue_delay_ms_max,excluded.queue_delay_ms_max),
 processing_ms_total=permission_shadow_observation_metrics.processing_ms_total+excluded.processing_ms_total,
 processing_ms_max=greatest(permission_shadow_observation_metrics.processing_ms_max,excluded.processing_ms_max),updated_at=now();
$$;

create or replace function phase5j_enqueue_shadow_observation(
 p_tenant_id varchar,p_comparison_id varchar,p_domain_code varchar,p_entry_point_id varchar,p_resource_type varchar,p_resource_id varchar,p_permission_code varchar,
 p_legacy_effect varchar,p_legacy_scope text,p_legacy_visibility varchar,p_legacy_reason varchar,p_legacy_complete boolean,p_legacy_error varchar,p_legacy_decision varchar,
 p_target_effect varchar,p_target_scope text,p_target_visibility varchar,p_target_reason varchar,p_target_complete boolean,p_target_error varchar,p_target_decision varchar,
 p_category varchar,p_severity varchar,p_correlation varchar,p_compared_at timestamptz)
returns varchar language plpgsql security definer set search_path=public,pg_temp as $$
declare v_previous text:=current_setting('app.current_tenant_id',true);v_settings permission_shadow_pipeline_settings%rowtype;v_risk varchar;v_mode varchar;v_bps integer;v_match_days integer;v_mismatch_days integer;v_hash varchar;v_bytes integer;v_pending bigint;v_queue uuid:=gen_random_uuid();v_sample boolean;v_priority integer;
begin
 perform set_config('app.current_tenant_id',p_tenant_id,true);
 if exists(select 1 from permission_shadow_observation_receipts where tenant_id=p_tenant_id and comparison_id=p_comparison_id) or exists(select 1 from permission_shadow_observation_ingress where tenant_id=p_tenant_id and comparison_id=p_comparison_id) then perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return 'DUPLICATE';end if;
 select * into v_settings from permission_shadow_pipeline_settings where singleton_id='ACTIVE';if not found or not v_settings.enabled then raise exception 'PHASE5J_PIPELINE_DISABLED';end if;
 select s.risk_lane,s.protection_mode,s.match_sample_bps,coalesce(p.match_retention_days,14),coalesce(p.mismatch_retention_days,180) into v_risk,v_mode,v_bps,v_match_days,v_mismatch_days
 from phase5i_resolve_shadow_sampling(p_permission_code,p_entry_point_id) s left join permission_shadow_sampling_policies p on p.risk_lane=s.risk_lane and p.protection_mode=s.protection_mode and p.active=true;
 v_hash:='sha256:'||encode(sha256(convert_to(concat_ws('|',p_tenant_id,p_comparison_id,p_domain_code,coalesce(p_entry_point_id,''),p_resource_type,p_resource_id,p_permission_code,p_category,p_correlation,p_compared_at::text),'UTF8')),'hex');
 v_bytes:=octet_length(concat_ws('|',p_legacy_scope,p_target_scope,p_legacy_reason,p_target_reason,coalesce(p_legacy_error,''),coalesce(p_target_error,'')));
 v_sample:=p_category<>'MATCH' or v_bps>=10000 or mod((hashtextextended(p_tenant_id||'|'||p_comparison_id,0)&9223372036854775807),10000)<v_bps;
 if not v_sample then
  perform phase5j_upsert_shadow_metric(p_tenant_id,p_domain_code,p_entry_point_id,p_permission_code,p_category,p_severity,v_risk,v_mode,1,0,0,0,0,0,p_compared_at);
  insert into permission_shadow_observation_receipts(tenant_id,comparison_id,storage_tier,observed_at,payload_hash) values(p_tenant_id,p_comparison_id,'METRICS_ONLY',p_compared_at,v_hash);
  perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return 'METRICS_ONLY';
 end if;
 perform pg_advisory_xact_lock(hashtextextended('phase5j-shadow-queue-capacity',0));
 select count(*) into v_pending from permission_shadow_observation_ingress;
 if v_pending>=v_settings.queue_capacity and p_category='MATCH' then
  perform phase5j_upsert_shadow_metric(p_tenant_id,p_domain_code,p_entry_point_id,p_permission_code,p_category,p_severity,v_risk,v_mode,1,1,0,0,0,0,p_compared_at);
  insert into permission_shadow_observation_receipts(tenant_id,comparison_id,storage_tier,observed_at,payload_hash) values(p_tenant_id,p_comparison_id,'DROPPED_BACKPRESSURE',p_compared_at,v_hash);
  insert into permission_shadow_pipeline_events(event_id,event_type,tenant_id,comparison_id,details) values(gen_random_uuid(),'MATCH_DROPPED_BACKPRESSURE',p_tenant_id,p_comparison_id,jsonb_build_object('queueCapacity',v_settings.queue_capacity,'pending',v_pending));
  perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return 'DROPPED_BACKPRESSURE';
 end if;
 if v_pending>=v_settings.queue_capacity and p_category<>'MATCH' then
  perform phase5j_ensure_shadow_partition('DURABLE_MISMATCH',p_compared_at::date);
  insert into permission_shadow_durable_mismatches values(p_tenant_id,p_comparison_id,p_domain_code,nullif(p_entry_point_id,''),p_resource_type,p_resource_id,p_permission_code,p_legacy_effect,coalesce(p_legacy_scope,''),p_legacy_visibility,p_legacy_reason,p_legacy_complete,nullif(p_legacy_error,''),nullif(p_legacy_decision,''),p_target_effect,coalesce(p_target_scope,''),p_target_visibility,p_target_reason,p_target_complete,nullif(p_target_error,''),p_target_decision,p_category,p_severity,v_risk,v_mode,p_correlation,p_compared_at,p_compared_at+make_interval(days=>v_mismatch_days),now(),v_hash) on conflict do nothing;
  insert into permission_shadow_observation_receipts(tenant_id,comparison_id,storage_tier,observed_at,payload_hash) values(p_tenant_id,p_comparison_id,'DURABLE_BYPASS',p_compared_at,v_hash);
  perform phase5j_upsert_shadow_metric(p_tenant_id,p_domain_code,p_entry_point_id,p_permission_code,p_category,p_severity,v_risk,v_mode,1,0,0,0,0,0,p_compared_at);
  insert into permission_shadow_pipeline_events(event_id,event_type,tenant_id,comparison_id,details) values(gen_random_uuid(),'MISMATCH_DURABLE_BACKPRESSURE_BYPASS',p_tenant_id,p_comparison_id,jsonb_build_object('queueCapacity',v_settings.queue_capacity,'pending',v_pending));
  perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return 'DURABLE_BYPASS';
 end if;
 v_priority:=case when p_category in('UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR') then 1000 when p_category<>'MATCH' then 800 else 100 end;
 insert into permission_shadow_observation_ingress(queue_id,tenant_id,comparison_id,domain_code,entry_point_id,resource_type,resource_id,permission_code,legacy_effect,legacy_scope,legacy_visibility,legacy_reason_code,legacy_context_complete,legacy_error_code,legacy_decision_id,target_effect,target_scope,target_visibility,target_reason_code,target_context_complete,target_error_code,target_decision_id,mismatch_category,severity,risk_lane,protection_mode,correlation_id,compared_at,match_retention_days,mismatch_retention_days,payload_hash,payload_bytes,priority)
 values(v_queue,p_tenant_id,p_comparison_id,p_domain_code,nullif(p_entry_point_id,''),p_resource_type,p_resource_id,p_permission_code,p_legacy_effect,coalesce(p_legacy_scope,''),p_legacy_visibility,p_legacy_reason,p_legacy_complete,nullif(p_legacy_error,''),nullif(p_legacy_decision,''),p_target_effect,coalesce(p_target_scope,''),p_target_visibility,p_target_reason,p_target_complete,nullif(p_target_error,''),p_target_decision,p_category,p_severity,v_risk,v_mode,p_correlation,p_compared_at,v_match_days,v_mismatch_days,v_hash,v_bytes,v_priority);
 perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return 'ENQUEUED';
exception when others then perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);raise;end $$;

create or replace function phase5j_process_shadow_observation_batch(p_worker_id varchar,p_requested_limit integer,p_actor varchar default null,p_reason text default null,p_correlation varchar default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_previous text:=current_setting('app.current_tenant_id',true);v_settings permission_shadow_pipeline_settings%rowtype;q permission_shadow_observation_ingress%rowtype;v_limit integer;v_processed integer:=0;v_retries integer:=0;v_dead integer:=0;v_duplicates integer:=0;v_delay bigint;v_started timestamptz;v_error text;v_attempt integer;
begin
 perform set_config('app.current_tenant_id','INSTANCE',true);select * into v_settings from permission_shadow_pipeline_settings where singleton_id='ACTIVE';if not found or not v_settings.enabled then perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return jsonb_build_object('status','DISABLED');end if;
 v_limit:=least(greatest(coalesce(p_requested_limit,v_settings.batch_size),1),v_settings.batch_size,5000);
 update permission_shadow_observation_ingress set status='PENDING',lease_owner=null,lease_until=null,available_at=now(),updated_at=now(),last_error=coalesce(last_error,'')||' | lease expired' where status='PROCESSING' and lease_until<now();
 for q in select * from permission_shadow_observation_ingress where status='PENDING' and available_at<=now() order by priority desc,enqueued_at for update skip locked limit v_limit loop
  v_started:=clock_timestamp();v_attempt:=q.attempts+1;
  update permission_shadow_observation_ingress set status='PROCESSING',attempts=v_attempt,lease_owner=p_worker_id,lease_until=now()+make_interval(secs=>v_settings.lease_seconds),updated_at=now() where queue_id=q.queue_id;
  begin
   perform set_config('app.current_tenant_id',q.tenant_id,true);
   if exists(select 1 from permission_shadow_observation_receipts where tenant_id=q.tenant_id and comparison_id=q.comparison_id) then delete from permission_shadow_observation_ingress where queue_id=q.queue_id;v_duplicates:=v_duplicates+1;continue;end if;
   if q.mismatch_category='MATCH' then
    perform phase5j_ensure_shadow_partition('SAMPLED_MATCH',q.compared_at::date);
    insert into permission_shadow_sampled_matches values(q.tenant_id,q.comparison_id,q.domain_code,q.entry_point_id,q.resource_type,q.resource_id,q.permission_code,q.legacy_effect,q.legacy_scope,q.legacy_visibility,q.legacy_reason_code,q.legacy_context_complete,q.legacy_error_code,q.legacy_decision_id,q.target_effect,q.target_scope,q.target_visibility,q.target_reason_code,q.target_context_complete,q.target_error_code,q.target_decision_id,q.mismatch_category,q.severity,q.risk_lane,q.protection_mode,q.correlation_id,q.compared_at,q.compared_at+make_interval(days=>q.match_retention_days),now(),q.payload_hash) on conflict do nothing;
    insert into permission_shadow_observation_receipts(tenant_id,comparison_id,storage_tier,observed_at,payload_hash,queue_id) values(q.tenant_id,q.comparison_id,'SAMPLED_MATCH',q.compared_at,q.payload_hash,q.queue_id);
   else
    perform phase5j_ensure_shadow_partition('DURABLE_MISMATCH',q.compared_at::date);
    insert into permission_shadow_durable_mismatches values(q.tenant_id,q.comparison_id,q.domain_code,q.entry_point_id,q.resource_type,q.resource_id,q.permission_code,q.legacy_effect,q.legacy_scope,q.legacy_visibility,q.legacy_reason_code,q.legacy_context_complete,q.legacy_error_code,q.legacy_decision_id,q.target_effect,q.target_scope,q.target_visibility,q.target_reason_code,q.target_context_complete,q.target_error_code,q.target_decision_id,q.mismatch_category,q.severity,q.risk_lane,q.protection_mode,q.correlation_id,q.compared_at,q.compared_at+make_interval(days=>q.mismatch_retention_days),now(),q.payload_hash) on conflict do nothing;
    insert into permission_shadow_observation_receipts(tenant_id,comparison_id,storage_tier,observed_at,payload_hash,queue_id) values(q.tenant_id,q.comparison_id,'DURABLE_MISMATCH',q.compared_at,q.payload_hash,q.queue_id);
   end if;
   v_delay:=greatest((extract(epoch from(v_started-q.enqueued_at))*1000)::bigint,0);
   perform phase5j_upsert_shadow_metric(q.tenant_id,q.domain_code,q.entry_point_id,q.permission_code,q.mismatch_category,q.severity,q.risk_lane,q.protection_mode,1,0,case when v_attempt>1 then 1 else 0 end,0,v_delay,(extract(epoch from(clock_timestamp()-v_started))*1000)::bigint,q.compared_at);
   delete from permission_shadow_observation_ingress where queue_id=q.queue_id;v_processed:=v_processed+1;
  exception when others then
   v_error:=left(sqlstate||':'||sqlerrm,4000);perform set_config('app.current_tenant_id','INSTANCE',true);
   if v_attempt>=v_settings.maximum_attempts then
    perform phase5j_ensure_shadow_partition('DEAD_LETTER',current_date);
    insert into permission_shadow_observation_dead_letters(failed_at,dead_letter_id,queue_id,tenant_id,comparison_id,mismatch_category,severity,attempts,error_code,error_message,payload) values(now(),gen_random_uuid(),q.queue_id,q.tenant_id,q.comparison_id,q.mismatch_category,q.severity,v_attempt,sqlstate,left(sqlerrm,4000),to_jsonb(q));
    perform set_config('app.current_tenant_id',q.tenant_id,true);
    insert into permission_shadow_observation_receipts(tenant_id,comparison_id,storage_tier,observed_at,payload_hash,queue_id) values(q.tenant_id,q.comparison_id,'DEAD_LETTER',q.compared_at,q.payload_hash,q.queue_id) on conflict do nothing;
    perform phase5j_upsert_shadow_metric(q.tenant_id,q.domain_code,q.entry_point_id,q.permission_code,q.mismatch_category,q.severity,q.risk_lane,q.protection_mode,0,0,1,1,0,0,now());
    perform set_config('app.current_tenant_id','INSTANCE',true);insert into permission_shadow_pipeline_events(event_id,event_type,tenant_id,comparison_id,queue_id,details) values(gen_random_uuid(),'OBSERVATION_DEAD_LETTERED',q.tenant_id,q.comparison_id,q.queue_id,jsonb_build_object('attempts',v_attempt,'error',v_error));delete from permission_shadow_observation_ingress where queue_id=q.queue_id;v_dead:=v_dead+1;
   else
    update permission_shadow_observation_ingress set status='PENDING',available_at=now()+make_interval(secs=>least(v_settings.retry_base_seconds*(2^least(v_attempt-1,10))::integer,3600)),lease_owner=null,lease_until=null,last_error=v_error,updated_at=now() where queue_id=q.queue_id;v_retries:=v_retries+1;
   end if;
  end;
  perform set_config('app.current_tenant_id','INSTANCE',true);
 end loop;
 if p_actor is not null and p_reason is not null then
  insert into permission_shadow_pipeline_events(event_id,event_type,tenant_id,details) values(gen_random_uuid(),'MANUAL_BATCH_PROCESSED',null,jsonb_build_object('workerId',p_worker_id,'processed',v_processed,'retries',v_retries,'deadLetters',v_dead,'duplicates',v_duplicates,'actorId',p_actor,'auditReason',p_reason,'correlationId',p_correlation));
 end if;
 perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return jsonb_build_object('status','OK','processed',v_processed,'retries',v_retries,'deadLetters',v_dead,'duplicates',v_duplicates,'workerId',p_worker_id);
exception when others then perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);raise;end $$;

create or replace function phase5j_archive_eligible_partitions(p_parent varchar,p_tier varchar,p_actor varchar,p_reason text)
returns integer language plpgsql security definer set search_path=public,pg_temp as $$
declare r record;v_count bigint;v_max timestamptz;v_archived integer:=0;v_day date;v_purge integer;v_dead_days integer;v_archive_name varchar;
begin
 if p_parent not in('permission_shadow_durable_mismatches','permission_shadow_sampled_matches','permission_shadow_observation_dead_letters') then raise exception 'PHASE5J_ARCHIVE_PARENT_INVALID';end if;
 select archive_purge_days,dead_letter_retention_days into v_purge,v_dead_days from permission_shadow_pipeline_settings where singleton_id='ACTIVE';
 for r in select c.relname child from pg_inherits i join pg_class p on p.oid=i.inhparent join pg_class c on c.oid=i.inhrelid join pg_namespace n on n.oid=c.relnamespace where p.relname=p_parent and n.nspname='public' order by c.relname loop
  if p_parent='permission_shadow_observation_dead_letters' then execute format('select count(*),max(failed_at) from %I',r.child) into v_count,v_max;else execute format('select count(*),max(retention_until) from %I',r.child) into v_count,v_max;end if;
  v_day:=to_date(right(r.child,8),'YYYYMMDD');
  if v_day<current_date and (v_count=0 or v_max<now()) and (p_parent<>'permission_shadow_observation_dead_letters' or v_day<current_date-v_dead_days) then
   v_archive_name:='p5j_arc_'||to_char(v_day,'YYYYMMDD')||'_'||substr(md5(p_parent||':'||r.child),1,16);
   execute format('alter table %I detach partition %I',p_parent,r.child);
   execute format('alter table %I rename to %I',r.child,v_archive_name);
   execute format('alter table public.%I owner to opendispatch_migration',v_archive_name);
   execute format('revoke all on table public.%I from public',v_archive_name);
   execute format('revoke all on table public.%I from opendispatch_runtime',v_archive_name);
   insert into permission_shadow_partition_archives(archive_id,storage_tier,parent_table,partition_name,partition_day,row_count,maximum_retention_until,archive_schema,archived_at,archived_by,audit_reason,purge_after) values(gen_random_uuid(),p_tier,p_parent,v_archive_name,v_day,v_count,v_max,'public',now(),p_actor,p_reason,now()+make_interval(days=>v_purge)) on conflict(parent_table,partition_name) do nothing;v_archived:=v_archived+1;
  end if;
 end loop;return v_archived;
end $$;

create or replace function phase5j_run_shadow_retention(p_actor varchar,p_reason text,p_correlation varchar)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_previous text:=current_setting('app.current_tenant_id',true);v_id uuid:=gen_random_uuid();s permission_shadow_pipeline_settings%rowtype;v_metrics bigint:=0;v_receipts bigint:=0;v_dead bigint:=0;v_archived integer:=0;v_purged integer:=0;r record;
begin
 perform set_config('app.current_tenant_id','INSTANCE',true);select * into s from permission_shadow_pipeline_settings where singleton_id='ACTIVE';insert into permission_shadow_retention_runs(run_id,status,started_at,actor_id,audit_reason,correlation_id) values(v_id,'RUNNING',now(),p_actor,p_reason,p_correlation);
 delete from permission_shadow_observation_metrics where bucket_start<now()-make_interval(days=>s.metrics_retention_days);get diagnostics v_metrics=row_count;
 perform set_config('app.phase5j_retention','true',true);delete from permission_shadow_observation_receipts where ingested_at<now()-make_interval(days=>s.receipt_retention_days);get diagnostics v_receipts=row_count;perform set_config('app.phase5j_retention','false',true);
 v_archived:=phase5j_archive_eligible_partitions('permission_shadow_durable_mismatches','DURABLE_MISMATCH',p_actor,p_reason)+phase5j_archive_eligible_partitions('permission_shadow_sampled_matches','SAMPLED_MATCH',p_actor,p_reason);
 -- Dead-letter partitions are archived only after their configured retention window.
 for r in select c.relname child from pg_inherits i join pg_class p on p.oid=i.inhparent join pg_class c on c.oid=i.inhrelid join pg_namespace n on n.oid=c.relnamespace where p.relname='permission_shadow_observation_dead_letters' and n.nspname='public' loop
  if to_date(right(r.child,8),'YYYYMMDD')<current_date-s.dead_letter_retention_days then v_archived:=v_archived+phase5j_archive_eligible_partitions('permission_shadow_observation_dead_letters','DEAD_LETTER',p_actor,p_reason);exit;end if;
 end loop;
 for r in select * from permission_shadow_partition_archives where status='ARCHIVED' and purge_after<now() for update loop execute format('drop table if exists %I.%I',r.archive_schema,r.partition_name);update permission_shadow_partition_archives set status='PURGED',purged_at=now() where archive_id=r.archive_id;v_purged:=v_purged+1;end loop;
 update permission_shadow_retention_runs set status='COMPLETED',metrics_deleted=v_metrics,receipts_deleted=v_receipts,dead_letters_deleted=v_dead,partitions_archived=v_archived,partitions_purged=v_purged,completed_at=now(),details=jsonb_build_object('metricsRetentionDays',s.metrics_retention_days,'receiptRetentionDays',s.receipt_retention_days,'deadLetterRetentionDays',s.dead_letter_retention_days) where run_id=v_id;
 perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return v_id;
exception when others then perform set_config('app.current_tenant_id','INSTANCE',true);update permission_shadow_retention_runs set status='FAILED',completed_at=now(),details=jsonb_build_object('error',sqlstate||':'||sqlerrm) where run_id=v_id;perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);raise;end $$;

create or replace function phase5j_generate_runtime_certification_evidence(
 p_source_version varchar,p_postgresql_clean varchar,p_postgresql_upgrade varchar,p_context varchar,p_ui varchar,p_playwright varchar,p_load varchar,p_pipeline varchar,p_evidence jsonb,p_actor varchar,p_reason text,p_correlation varchar)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_previous text:=current_setting('app.current_tenant_id',true);v_id uuid:=gen_random_uuid();v_catalog uuid;v_manifest uuid;v_status varchar;
begin
 perform set_config('app.current_tenant_id','INSTANCE',true);select revision_id into v_catalog from permission_catalog_active_revision where singleton_id='ACTIVE';select manifest_id into v_manifest from permission_application_manifests where status='ACTIVE' order by activated_at desc nulls last limit 1;
 v_status:=case when p_postgresql_clean='PASS' and p_postgresql_upgrade='PASS' and p_context='PASS' and p_ui='PASS' and p_playwright='PASS' and p_load='PASS' and p_pipeline='HEALTHY' then 'CERTIFIED' when 'FAIL' in(p_postgresql_clean,p_postgresql_upgrade,p_context,p_ui,p_playwright,p_load) or p_pipeline='UNHEALTHY' then 'FAILED' else 'INCOMPLETE' end;
 insert into permission_phase5_runtime_certification_evidence(evidence_id,status,source_version,catalog_revision_id,manifest_id,postgresql_clean_status,postgresql_upgrade_status,application_context_status,admin_ui_build_status,playwright_status,load_test_status,pipeline_status,evidence,actor_id,audit_reason,correlation_id) values(v_id,v_status,p_source_version,v_catalog,v_manifest,p_postgresql_clean,p_postgresql_upgrade,p_context,p_ui,p_playwright,p_load,p_pipeline,coalesce(p_evidence,'{}'::jsonb),p_actor,p_reason,p_correlation);
 perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);return v_id;
exception when others then perform set_config('app.current_tenant_id',coalesce(v_previous,''),true);raise;end $$;

-- Tenant evidence tables allow tenant ingestion and Platform observation. Governance/configuration remains INSTANCE-only.
do $$declare t text;begin
 foreach t in array array['permission_shadow_observation_ingress','permission_shadow_observation_receipts','permission_shadow_observation_metrics','permission_shadow_durable_mismatches','permission_shadow_sampled_matches','permission_shadow_observation_dead_letters'] loop
  execute format('alter table %I enable row level security',t);execute format('alter table %I force row level security',t);execute format('create policy phase5j_tenant_scope on %I using(iam_current_tenant_id()=''INSTANCE'' or tenant_id=iam_current_tenant_id()) with check(iam_current_tenant_id()=''INSTANCE'' or tenant_id=iam_current_tenant_id())',t);
 end loop;
 alter table permission_shadow_pipeline_settings enable row level security;alter table permission_shadow_pipeline_settings force row level security;
 create policy phase5j_settings_read on permission_shadow_pipeline_settings for select using(true);
 create policy phase5j_settings_write on permission_shadow_pipeline_settings for all using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
 foreach t in array array['permission_shadow_partition_archives','permission_shadow_retention_runs','permission_phase5_runtime_certification_evidence'] loop
  execute format('alter table %I enable row level security',t);execute format('alter table %I force row level security',t);execute format('create policy phase5j_instance_scope on %I using(iam_current_tenant_id()=''INSTANCE'') with check(iam_current_tenant_id()=''INSTANCE'')',t);
 end loop;
 alter table permission_shadow_pipeline_events enable row level security;alter table permission_shadow_pipeline_events force row level security;
 create policy phase5j_event_scope on permission_shadow_pipeline_events using(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id()) with check(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id());
end $$;

revoke all on function phase5j_ensure_shadow_partition(varchar,date) from public;grant execute on function phase5j_ensure_shadow_partition(varchar,date) to public;
revoke all on function phase5j_ensure_shadow_partitions(date,date) from public;grant execute on function phase5j_ensure_shadow_partitions(date,date) to public;
revoke all on function phase5j_enqueue_shadow_observation(varchar,varchar,varchar,varchar,varchar,varchar,varchar,varchar,text,varchar,varchar,boolean,varchar,varchar,varchar,text,varchar,varchar,boolean,varchar,varchar,varchar,varchar,varchar,timestamptz) from public;grant execute on function phase5j_enqueue_shadow_observation(varchar,varchar,varchar,varchar,varchar,varchar,varchar,varchar,text,varchar,varchar,boolean,varchar,varchar,varchar,text,varchar,varchar,boolean,varchar,varchar,varchar,varchar,varchar,timestamptz) to public;
revoke all on function phase5j_process_shadow_observation_batch(varchar,integer,varchar,text,varchar) from public;grant execute on function phase5j_process_shadow_observation_batch(varchar,integer,varchar,text,varchar) to public;
revoke all on function phase5j_run_shadow_retention(varchar,text,varchar) from public;grant execute on function phase5j_run_shadow_retention(varchar,text,varchar) to public;
revoke all on function phase5j_generate_runtime_certification_evidence(varchar,varchar,varchar,varchar,varchar,varchar,varchar,varchar,jsonb,varchar,text,varchar) from public;grant execute on function phase5j_generate_runtime_certification_evidence(varchar,varchar,varchar,varchar,varchar,varchar,varchar,varchar,jsonb,varchar,text,varchar) to public;
