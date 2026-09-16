-- Phase 5I enforcement: constraints, RLS, append-only evidence and fail-closed evaluation functions.

alter table permission_shadow_sampling_policies add constraint ck_shadow_sampling_bps check(match_sample_bps between 0 and 10000);
alter table permission_shadow_sampling_policies add constraint ck_shadow_mismatch_durable check(mismatch_durable=true);
alter table permission_shadow_sampling_policies add constraint ck_shadow_sampling_retention check(match_retention_days between 1 and 365 and mismatch_retention_days between match_retention_days and 730);
alter table resource_shadow_decision_comparisons_v2 add constraint ck_shadow_v2_domain check(domain_code in('TASK','A2A','AGENT','ISSUE','INTEGRATION'));
alter table resource_shadow_decision_comparisons_v2 add constraint ck_shadow_v2_category check(mismatch_category in('MATCH','UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR','CONTEXT_INCOMPLETE','UNEXPECTED_DENY','REASON_DIFFERENT','LEGACY_UNAVAILABLE'));
alter table resource_shadow_decision_comparisons_v2 add constraint ck_shadow_v2_severity check(severity in('INFO','LOW','MEDIUM','HIGH','CRITICAL'));
alter table resource_shadow_decision_comparisons_v2 add constraint ck_shadow_v2_visibility check(legacy_visibility in('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA') and target_visibility in('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA'));
alter table permission_shadow_mismatch_cases add constraint ck_mismatch_case_status check(status in('OPEN','TRIAGED','IN_REMEDIATION','AWAITING_VALIDATION','WAIVED','RESOLVED','REOPENED'));
alter table permission_shadow_mismatch_cases add constraint ck_mismatch_case_category check(category in('UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR','CONTEXT_INCOMPLETE','UNEXPECTED_DENY','REASON_DIFFERENT','LEGACY_UNAVAILABLE'));
alter table permission_shadow_mismatch_cases add constraint ck_mismatch_case_severity check(severity in('LOW','MEDIUM','HIGH','CRITICAL'));
alter table permission_shadow_mismatch_waivers add constraint ck_mismatch_waiver_status check(status in('ACTIVE','REVOKED','EXPIRED'));
alter table permission_shadow_mismatch_waivers add constraint ck_mismatch_waiver_window check(expires_at>approved_at and expires_at<=approved_at+interval '30 days');
alter table permission_shadow_regression_evidence add constraint ck_regression_result check(result in('PASSED','FAILED'));
alter table permission_domain_readiness_evidence add constraint ck_domain_readiness_status check(status in('ELIGIBLE','BLOCKED','OBSERVING','NO_EVIDENCE'));
alter table permission_phase6_eligibility_evidence add constraint ck_phase6_status check(status in('ELIGIBLE','BLOCKED'));
alter table permission_domain_readiness_thresholds add constraint ck_domain_evidence_age check(maximum_evidence_age_hours between 1 and 168);

create or replace function phase5i_reject_append_only_mutation() returns trigger language plpgsql as $$ begin raise exception 'PHASE5I_EVIDENCE_APPEND_ONLY' using errcode='55000'; end $$;
create trigger trg_shadow_v2_immutable before update or delete on resource_shadow_decision_comparisons_v2 for each row execute function phase5i_reject_append_only_mutation();
create trigger trg_shadow_case_event_immutable before update or delete on permission_shadow_case_events for each row execute function phase5i_reject_append_only_mutation();
create trigger trg_shadow_regression_immutable before update or delete on permission_shadow_regression_evidence for each row execute function phase5i_reject_append_only_mutation();
create trigger trg_domain_readiness_immutable before update or delete on permission_domain_readiness_evidence for each row execute function phase5i_reject_append_only_mutation();
create trigger trg_phase6_eligibility_immutable before update or delete on permission_phase6_eligibility_evidence for each row execute function phase5i_reject_append_only_mutation();

create or replace function phase5i_append_case_event() returns trigger language plpgsql as $$
begin
 insert into permission_shadow_case_events(event_id,case_id,event_type,previous_value,current_value,actor_id,audit_reason,correlation_id,occurred_at)
 values(gen_random_uuid(),new.case_id,case when TG_OP='INSERT' then 'CASE_CREATED' else 'CASE_UPDATED' end,
   case when TG_OP='INSERT' then null else to_jsonb(old) end,to_jsonb(new),
   coalesce(nullif(current_setting('app.current_actor_id',true),''),new.updated_by),
   coalesce(nullif(current_setting('app.current_audit_reason',true),''),'Phase 5I mismatch governance update'),
   nullif(current_setting('app.current_correlation_id',true),''),now());
 return new;
end $$;
create trigger trg_phase5i_case_event after insert or update on permission_shadow_mismatch_cases for each row execute function phase5i_append_case_event();

create or replace function phase5i_append_waiver_event() returns trigger language plpgsql as $$
begin
 insert into permission_shadow_case_events(event_id,case_id,event_type,current_value,actor_id,audit_reason,correlation_id,occurred_at)
 values(gen_random_uuid(),new.case_id,'WAIVER_CREATED',to_jsonb(new),new.approved_by,
   coalesce(nullif(current_setting('app.current_audit_reason',true),''),new.reason),nullif(current_setting('app.current_correlation_id',true),''),now());
 return new;
end $$;
create trigger trg_phase5i_waiver_event after insert on permission_shadow_mismatch_waivers for each row execute function phase5i_append_waiver_event();

create or replace function phase5i_append_regression_event() returns trigger language plpgsql as $$
begin
 insert into permission_shadow_case_events(event_id,case_id,event_type,current_value,actor_id,audit_reason,correlation_id,occurred_at)
 values(gen_random_uuid(),new.case_id,'REGRESSION_EVIDENCE_ADDED',to_jsonb(new),new.executed_by,
   coalesce(nullif(current_setting('app.current_audit_reason',true),''),'Regression evidence added'),nullif(current_setting('app.current_correlation_id',true),''),now());
 return new;
end $$;
create trigger trg_phase5i_regression_event after insert on permission_shadow_regression_evidence for each row execute function phase5i_append_regression_event();

create or replace function phase5i_guard_waiver() returns trigger language plpgsql as $$
declare category_value varchar; severity_value varchar;
begin
 select category,severity into category_value,severity_value from permission_shadow_mismatch_cases where case_id=new.case_id;
 if category_value in('UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR') or severity_value='CRITICAL' then
  raise exception 'CRITICAL_MISMATCH_WAIVER_FORBIDDEN' using errcode='23514';
 end if;
 return new;
end $$;
create trigger trg_phase5i_guard_waiver before insert or update on permission_shadow_mismatch_waivers for each row execute function phase5i_guard_waiver();

create or replace function phase5i_domain_matches_entry(
 p_domain varchar,p_resource_type varchar,p_owner_module varchar,p_entry_point_id varchar,p_display_name varchar,p_source_ref varchar)
returns boolean language sql immutable as $$
 with normalized as (
  select upper(coalesce(p_resource_type,'')) resource_type,
         lower(concat_ws('|',p_owner_module,p_entry_point_id,p_display_name,p_source_ref)) haystack
 )
 select case upper(coalesce(p_domain,''))
  when 'TASK' then resource_type like '%TASK%' or haystack like '%task%'
  when 'A2A' then resource_type like '%A2A%' or resource_type like '%HANDOFF%' or haystack like '%a2a%' or haystack like '%handoff%'
  when 'AGENT' then resource_type like '%AGENT%' or haystack like '%agent%'
  when 'ISSUE' then resource_type like '%ISSUE%' or haystack like '%issue%' or haystack like '%redmine%' or haystack like '%jira%'
  when 'INTEGRATION' then resource_type like '%INTEGRATION%' or resource_type like '%PROVIDER%' or resource_type like '%CONNECTION%'
    or haystack like '%integration%' or haystack like '%provider%' or haystack like '%connector%' or haystack like '%webhook%'
    or haystack like '%relay%' or haystack like '%externalchange%' or haystack like '%external_change%' or haystack like '%projectmapping%'
    or haystack like '%project_mapping%' or haystack like '%adapter%'
  else false end from normalized;
$$;

create or replace function phase5i_evaluate_domain_readiness(p_tenant_id varchar,p_domain varchar,p_from timestamptz,p_to timestamptz,p_actor varchar,p_correlation varchar,p_audit_reason text)
returns uuid language plpgsql as $$
declare v_id uuid:=gen_random_uuid();v_threshold permission_domain_readiness_thresholds%rowtype;v_sample bigint;v_match bigint;v_ua bigint;v_scope bigint;v_visibility bigint;v_target bigint;v_context bigint;v_ud bigint;v_reason bigint;v_cases bigint;v_waivers bigint;v_failed bigint;v_coverage numeric(7,4);v_drift bigint;v_entry bigint;v_expired bigint;v_catalog uuid;v_manifest uuid;v_status varchar;v_blockers jsonb;
begin
 if p_to<=p_from then raise exception 'DOMAIN_READINESS_WINDOW_INVALID' using errcode='23514'; end if;
 if p_domain not in('TASK','A2A','AGENT','ISSUE','INTEGRATION') then raise exception 'DOMAIN_READINESS_DOMAIN_INVALID' using errcode='23514'; end if;
 select * into v_threshold from permission_domain_readiness_thresholds where domain_code=p_domain and active=true;
 if not found then raise exception 'DOMAIN_READINESS_THRESHOLD_MISSING' using errcode='23514'; end if;
 select count(*),count(*) filter(where o.mismatch_category='MATCH'),count(*) filter(where o.mismatch_category='UNEXPECTED_ALLOW'),count(*) filter(where o.mismatch_category='SCOPE_WIDENED'),count(*) filter(where o.mismatch_category='VISIBILITY_WIDENED'),count(*) filter(where o.mismatch_category='TARGET_ERROR'),
 count(*) filter(where o.mismatch_category='CONTEXT_INCOMPLETE' and not exists(
  select 1 from permission_shadow_mismatch_cases c join permission_shadow_mismatch_waivers w on w.case_id=c.case_id
  where c.source_tenant_id=o.tenant_id and c.comparison_id=o.comparison_id and w.status='ACTIVE' and w.expires_at>now())),
 count(*) filter(where o.mismatch_category='UNEXPECTED_DENY'),count(*) filter(where o.mismatch_category='REASON_DIFFERENT')
 into v_sample,v_match,v_ua,v_scope,v_visibility,v_target,v_context,v_ud,v_reason from resource_shadow_decision_comparisons_v2 o where o.tenant_id=p_tenant_id and o.domain_code=p_domain and o.compared_at>=p_from and o.compared_at<p_to;
 select count(*) into v_cases from permission_shadow_mismatch_cases c where c.source_tenant_id=p_tenant_id and c.domain_code=p_domain and c.status<>'RESOLVED' and c.category in('UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR','CONTEXT_INCOMPLETE') and not exists(select 1 from permission_shadow_mismatch_waivers w where w.case_id=c.case_id and w.status='ACTIVE' and w.expires_at>now());
 select count(*) into v_waivers from permission_shadow_mismatch_waivers w join permission_shadow_mismatch_cases c on c.case_id=w.case_id where c.source_tenant_id=p_tenant_id and c.domain_code=p_domain and w.status='ACTIVE' and w.expires_at>now();
 select count(*) into v_failed from (
  select distinct on(r.case_id) r.case_id,r.result from permission_shadow_regression_evidence r
  join permission_shadow_mismatch_cases c on c.case_id=r.case_id
  where c.source_tenant_id=p_tenant_id and c.domain_code=p_domain order by r.case_id,r.executed_at desc,r.created_at desc
 ) latest_regression where result='FAILED';
 select m.manifest_id,m.coverage_percent into v_manifest,v_coverage from permission_application_manifests m where m.status='ACTIVE' order by m.activated_at desc nulls last limit 1;
 select count(*) into v_drift from permission_manifest_entry_runtime_drift d left join permission_entry_point_inventory i on i.entry_point_id=d.entry_point_id where d.manifest_id=v_manifest and d.blocker and phase5i_domain_matches_entry(p_domain,coalesce(i.resource_type,''),d.owner_module,d.entry_point_id,d.display_name,d.source_ref);
 select revision_id into v_catalog from permission_catalog_active_revision where singleton_id='ACTIVE';
 select count(*) filter(where unknown_permission or missing_mapping or missing_resolver or overdue or expired_bypass),count(*) filter(where expired_bypass) into v_entry,v_expired from permission_entry_point_readiness e where phase5i_domain_matches_entry(p_domain,e.resource_type,e.owner_module,e.entry_point_id,e.display_name,e.source_ref);
 v_blockers=jsonb_strip_nulls(jsonb_build_object('MANIFEST_COVERAGE',case when coalesce(v_coverage,0)<100 then coalesce(v_coverage,0) end,'UNEXPECTED_ALLOW',nullif(v_ua,0),'SCOPE_WIDENED',nullif(v_scope,0),'VISIBILITY_WIDENED',nullif(v_visibility,0),'TARGET_ERROR',nullif(v_target,0),'CONTEXT_INCOMPLETE',nullif(v_context,0),'OPEN_BLOCKING_CASES',nullif(v_cases,0),'FAILED_REGRESSION',nullif(v_failed,0),'RUNTIME_DRIFT',nullif(v_drift,0),'ENTRY_POINT_BLOCKERS',nullif(v_entry,0),'EXPIRED_BYPASSES',nullif(v_expired,0)));
 if v_sample=0 then v_status='NO_EVIDENCE';
 elsif v_sample<v_threshold.minimum_samples or extract(epoch from(p_to-p_from))/3600<v_threshold.minimum_window_hours then v_status='OBSERVING';
 elsif coalesce(v_coverage,0)<100 or v_ua+v_scope+v_visibility+v_target+v_context+v_cases+v_failed+v_drift+v_entry+v_expired>0 or (v_sample>0 and v_ud*10000/v_sample>v_threshold.maximum_unexpected_deny_bps) then v_status='BLOCKED';
 else v_status='ELIGIBLE'; end if;
 insert into permission_domain_readiness_evidence(evidence_id,source_tenant_id,domain_code,status,window_started_at,window_ended_at,sample_count,match_count,unexpected_allow_count,scope_widened_count,visibility_widened_count,target_error_count,context_incomplete_count,unexpected_deny_count,reason_different_count,open_blocking_cases,active_waivers,failed_regressions,manifest_coverage_percent,runtime_drift_blockers,entry_point_blockers,expired_bypasses,blockers,catalog_revision_id,manifest_id,actor_id,audit_reason,correlation_id,evaluated_at) values(v_id,p_tenant_id,p_domain,v_status,p_from,p_to,v_sample,v_match,v_ua,v_scope,v_visibility,v_target,v_context,v_ud,v_reason,v_cases,v_waivers,v_failed,coalesce(v_coverage,0),coalesce(v_drift,0),coalesce(v_entry,0),coalesce(v_expired,0),v_blockers,v_catalog,v_manifest,p_actor,p_audit_reason,p_correlation,now());
 insert into permission_shadow_case_events(event_id,case_id,event_type,current_value,actor_id,audit_reason,correlation_id,occurred_at)
 select gen_random_uuid(),c.case_id,'DOMAIN_READINESS_EVALUATED',jsonb_build_object('domain',p_domain,'status',v_status,'evidenceId',v_id),p_actor,p_audit_reason,p_correlation,now() from permission_shadow_mismatch_cases c where false;
 return v_id;
end $$;

create or replace function phase5i_evaluate_phase6_eligibility(p_tenant_id varchar,p_actor varchar,p_correlation varchar,p_audit_reason text)
returns uuid language plpgsql as $$
declare v_id uuid:=gen_random_uuid();v_catalog uuid;v_manifest uuid;v_coverage numeric(7,4);v_drift bigint;v_entry bigint;v_expired bigint;v_cases bigint;v_eligible bigint;v_blocked bigint;v_observing bigint;v_status varchar;v_blockers jsonb;v_domain_refs jsonb;
begin
 select revision_id into v_catalog from permission_catalog_active_revision where singleton_id='ACTIVE';
 select manifest_id,coverage_percent,runtime_drift_blockers into v_manifest,v_coverage,v_drift from permission_application_manifest_readiness where status='ACTIVE' order by activated_at desc nulls last limit 1;
 select count(*) filter(where unknown_permission or missing_mapping or missing_resolver or overdue or expired_bypass),count(*) filter(where expired_bypass) into v_entry,v_expired from permission_entry_point_readiness;
 select count(*) into v_cases from permission_shadow_mismatch_cases c where source_tenant_id=p_tenant_id and status<>'RESOLVED' and category in('UNEXPECTED_ALLOW','SCOPE_WIDENED','VISIBILITY_WIDENED','TARGET_ERROR','CONTEXT_INCOMPLETE') and not exists(select 1 from permission_shadow_mismatch_waivers w where w.case_id=c.case_id and w.status='ACTIVE' and w.expires_at>now());
 with required(domain_code) as(values('TASK'),('A2A'),('AGENT'),('ISSUE'),('INTEGRATION')),latest as(select distinct on(e.domain_code) e.domain_code,e.evidence_id,e.status from permission_domain_readiness_evidence e join permission_domain_readiness_thresholds t on t.domain_code=e.domain_code and t.active=true where e.source_tenant_id=p_tenant_id and e.catalog_revision_id=v_catalog and e.manifest_id is not distinct from v_manifest and e.evaluated_at>=now()-make_interval(hours=>t.maximum_evidence_age_hours) and e.window_ended_at>=now()-make_interval(hours=>t.maximum_evidence_age_hours) order by e.domain_code,e.evaluated_at desc)
 select count(*) filter(where l.status='ELIGIBLE'),count(*) filter(where coalesce(l.status,'NO_EVIDENCE') in('BLOCKED','NO_EVIDENCE')),count(*) filter(where l.status='OBSERVING'),jsonb_object_agg(r.domain_code,jsonb_build_object('evidenceId',l.evidence_id,'status',coalesce(l.status,'NO_EVIDENCE'))) into v_eligible,v_blocked,v_observing,v_domain_refs from required r left join latest l using(domain_code);
 v_blockers=jsonb_strip_nulls(jsonb_build_object('BLOCKED_DOMAINS',nullif(v_blocked,0),'OBSERVING_DOMAINS',nullif(v_observing,0),'RUNTIME_DRIFT',nullif(v_drift,0),'ENTRY_POINT_BLOCKERS',nullif(v_entry,0),'EXPIRED_BYPASSES',nullif(v_expired,0),'OPEN_BLOCKING_CASES',nullif(v_cases,0),'MANIFEST_COVERAGE',case when coalesce(v_coverage,0)<100 then coalesce(v_coverage,0) end));
 if v_eligible=5 and v_blocked=0 and v_observing=0 and coalesce(v_coverage,0)=100 and coalesce(v_drift,0)=0 and coalesce(v_entry,0)=0 and coalesce(v_expired,0)=0 and v_cases=0 then v_status='ELIGIBLE'; else v_status='BLOCKED'; end if;
 insert into permission_phase6_eligibility_evidence(evidence_id,source_tenant_id,status,required_domains,domain_evidence_refs,eligible_domains,blocked_domains,observing_domains,catalog_revision_id,manifest_id,manifest_coverage_percent,runtime_drift_blockers,entry_point_blockers,expired_bypasses,open_blocking_cases,blockers,actor_id,audit_reason,correlation_id,evaluated_at) values(v_id,p_tenant_id,v_status,'["TASK","A2A","AGENT","ISSUE","INTEGRATION"]'::jsonb,v_domain_refs,v_eligible,v_blocked,v_observing,v_catalog,v_manifest,coalesce(v_coverage,0),coalesce(v_drift,0),coalesce(v_entry,0),coalesce(v_expired,0),v_cases,v_blockers,p_actor,p_audit_reason,p_correlation,now());
 return v_id;
end $$;

create or replace function phase5i_resolve_shadow_sampling(p_permission_code varchar,p_entry_point_id varchar)
returns table(risk_lane varchar,protection_mode varchar,match_sample_bps integer)
language sql stable security definer set search_path=public,pg_temp as $$
 with active as (
  select manifest_id from permission_application_manifests where status='ACTIVE' order by activated_at desc nulls last limit 1
 ), modes as (
  select e.protection_mode from active a join permission_application_manifest_entries e on e.manifest_id=a.manifest_id
  where e.permission_code=p_permission_code and (coalesce(p_entry_point_id,'')='' or e.entry_point_id=p_entry_point_id)
 ), resolved as (
  select case when count(*)=0 then 'LEGACY_AUTHORITY'
              when count(distinct protection_mode)=1 then min(protection_mode)
              else 'DUAL_SHADOW' end protection_mode from modes
 )
 select coalesce(p.risk_lane,'READ')::varchar,r.protection_mode::varchar,
   coalesce((select sp.match_sample_bps from permission_shadow_sampling_policies sp where sp.active=true and sp.risk_lane=coalesce(p.risk_lane,'READ') and sp.protection_mode=r.protection_mode limit 1),10000)::integer
 from resolved r cross join (select 1) x left join permission_definitions p on p.permission_code=p_permission_code;
$$;
revoke all on function phase5i_resolve_shadow_sampling(varchar,varchar) from public;
grant execute on function phase5i_resolve_shadow_sampling(varchar,varchar) to public;

alter table resource_shadow_decision_comparisons_v2 enable row level security;alter table resource_shadow_decision_comparisons_v2 force row level security;
create policy phase5i_shadow_tenant_scope on resource_shadow_decision_comparisons_v2 using(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

alter table permission_shadow_sampling_policies enable row level security;alter table permission_shadow_sampling_policies force row level security;
create policy phase5i_sampling_read on permission_shadow_sampling_policies for select using(true);
create policy phase5i_sampling_write on permission_shadow_sampling_policies for all using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');

alter table permission_shadow_mismatch_cases enable row level security;alter table permission_shadow_mismatch_cases force row level security;
create policy phase5i_cases_instance_scope on permission_shadow_mismatch_cases using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_shadow_mismatch_waivers enable row level security;alter table permission_shadow_mismatch_waivers force row level security;
create policy phase5i_waiver_instance_scope on permission_shadow_mismatch_waivers using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_shadow_regression_evidence enable row level security;alter table permission_shadow_regression_evidence force row level security;
create policy phase5i_regression_instance_scope on permission_shadow_regression_evidence using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_shadow_case_events enable row level security;alter table permission_shadow_case_events force row level security;
create policy phase5i_case_event_instance_scope on permission_shadow_case_events using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_domain_readiness_thresholds enable row level security;alter table permission_domain_readiness_thresholds force row level security;
create policy phase5i_threshold_instance_scope on permission_domain_readiness_thresholds using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_domain_readiness_evidence enable row level security;alter table permission_domain_readiness_evidence force row level security;
create policy phase5i_domain_evidence_instance_scope on permission_domain_readiness_evidence using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_phase6_eligibility_evidence enable row level security;alter table permission_phase6_eligibility_evidence force row level security;
create policy phase5i_phase6_evidence_instance_scope on permission_phase6_eligibility_evidence using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
