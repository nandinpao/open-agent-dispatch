-- Phase 5E enforcement: role ownership, Platform RLS, SoD, immutable evidence and review scheduling.

alter table rbac_roles drop constraint if exists ck_rbac_role_type;
alter table rbac_roles drop constraint if exists ck_rbac_role_ownership;
alter table rbac_roles
  add constraint ck_rbac_role_type check(role_type in('SYSTEM_ROLE','TENANT_ROLE','CUSTOM_PLATFORM_ROLE','CUSTOM_TENANT_ROLE','LEGACY_COMPATIBILITY_ROLE')),
  add constraint ck_rbac_role_ownership check(
    (role_type='CUSTOM_TENANT_ROLE' and tenant_id is not null and system_managed=false)
    or (role_type='CUSTOM_PLATFORM_ROLE' and tenant_id is null and system_managed=false)
    or (role_type not in('CUSTOM_TENANT_ROLE','CUSTOM_PLATFORM_ROLE') and tenant_id is null and system_managed=true)),
  add constraint ck_rbac_role_risk check(risk_level in('LOW','MEDIUM','HIGH','CRITICAL')),
  add constraint ck_rbac_role_review_time check(next_review_at is null or next_review_at>created_at);

alter table rbac_principal_role_bindings
  add constraint ck_rbac_binding_review_time check(next_review_at is null or next_review_at>created_at),
  add constraint ck_rbac_binding_review_actor check(last_reviewed_at is null or btrim(coalesce(last_reviewed_by,''))<>'');

alter table rbac_separation_of_duties_rules
  add constraint ck_rbac_sod_status check(status in('ACTIVE','DISABLED')),
  add constraint ck_rbac_sod_version check(version>0);

-- Tenant requests may read global templates; only INSTANCE context may mutate global Platform rows.
do $$
declare table_name text;
begin
  foreach table_name in array array['rbac_roles','rbac_role_permissions'] loop
    execute format('drop policy if exists rbac_select_scope on %I',table_name);
    execute format('drop policy if exists rbac_insert_tenant on %I',table_name);
    execute format('drop policy if exists rbac_update_tenant on %I',table_name);
    execute format('drop policy if exists rbac_delete_tenant on %I',table_name);
    execute format('create policy rbac_select_scope on %I for select using (tenant_id is null or tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_insert_tenant on %I for insert with check ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_update_tenant on %I for update using ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id()) with check ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id())',table_name);
    execute format('create policy rbac_delete_tenant on %I for delete using ((tenant_id is null and iam_current_tenant_id()=''INSTANCE'') or tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

drop policy if exists rbac_tenant_only on rbac_principal_role_bindings;
create policy rbac_binding_scope on rbac_principal_role_bindings
using((tenant_id is null and iam_current_tenant_id()='INSTANCE') or tenant_id=iam_current_tenant_id())
with check((tenant_id is null and iam_current_tenant_id()='INSTANCE') or tenant_id=iam_current_tenant_id());

alter table rbac_separation_of_duties_rules enable row level security;
alter table rbac_separation_of_duties_rules force row level security;
create policy rbac_sod_scope on rbac_separation_of_duties_rules
using(tenant_id is null or tenant_id=iam_current_tenant_id())
with check((tenant_id is null and iam_current_tenant_id()='INSTANCE') or tenant_id=iam_current_tenant_id());

alter table rbac_administration_events enable row level security;
alter table rbac_administration_events force row level security;
create policy rbac_admin_event_scope on rbac_administration_events
using((tenant_id is null and iam_current_tenant_id()='INSTANCE') or tenant_id=iam_current_tenant_id())
with check((tenant_id is null and iam_current_tenant_id()='INSTANCE') or tenant_id=iam_current_tenant_id());

create or replace function phase1a4_validate_role_permission()
returns trigger language plpgsql as $$
declare role_tenant varchar(64); role_kind varchar(40); role_status varchar(32); allowed varchar(32)[];
begin
  select tenant_id,role_type,status into role_tenant,role_kind,role_status from rbac_roles where role_id=new.role_id for key share;
  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_status<>'ACTIVE' then raise exception 'ROLE_DISABLED' using errcode='23514'; end if;
  if role_tenant is distinct from new.tenant_id then raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514'; end if;
  select allowed_scope_types into allowed from permission_definitions where permission_code=new.permission_point and active=true and lifecycle in('ACTIVE','DEPRECATED');
  if not found then raise exception 'AUTH_PERMISSION_UNKNOWN' using errcode='23503'; end if;
  if role_kind='CUSTOM_PLATFORM_ROLE' and not ('INSTANCE'=any(allowed)) then
    raise exception 'ROLE_PERMISSION_SCOPE_UNSUPPORTED' using errcode='23514';
  end if;
  if role_kind='CUSTOM_TENANT_ROLE' and 'INSTANCE'=any(allowed) then
    raise exception 'ROLE_INSTANCE_PERMISSION_FORBIDDEN' using errcode='23514';
  end if;
  return new;
end $$;

create or replace function phase5e_scopes_overlap(a_type varchar,a_id varchar,b_type varchar,b_id varchar)
returns boolean language sql immutable as $$
  select case
    when a_type='INSTANCE' or b_type='INSTANCE' then a_type=b_type
    when a_type='TENANT' or b_type='TENANT' then true
    else a_type=b_type and a_id=b_id end;
$$;

create or replace function phase5e_periods_overlap(a_start timestamptz,a_end timestamptz,b_start timestamptz,b_end timestamptz)
returns boolean language sql immutable as $$
  select a_start<coalesce(b_end,'infinity'::timestamptz) and b_start<coalesce(a_end,'infinity'::timestamptz);
$$;

create or replace function phase5e_validate_separation_of_duties()
returns trigger language plpgsql as $$
declare conflict_rule varchar(128);
begin
  if new.status<>'ACTIVE' then return new; end if;
  select rule.rule_id into conflict_rule
  from rbac_separation_of_duties_rules rule
  join rbac_principal_role_bindings existing
    on existing.binding_id<>new.binding_id and existing.principal_type=new.principal_type
   and existing.principal_id=new.principal_id and existing.status='ACTIVE'
   and existing.tenant_id is not distinct from new.tenant_id
   and phase5e_periods_overlap(existing.effective_at,existing.expires_at,new.effective_at,new.expires_at)
  where rule.status='ACTIVE' and (rule.tenant_id is null or rule.tenant_id is not distinct from new.tenant_id)
    and ((rule.left_role_id=new.role_id and rule.right_role_id=existing.role_id)
      or (rule.right_role_id=new.role_id and rule.left_role_id=existing.role_id))
    and (not rule.scope_overlap_required or phase5e_scopes_overlap(existing.scope_type,existing.scope_id,new.scope_type,new.scope_id))
  limit 1;
  if conflict_rule is not null then
    raise exception 'ROLE_SEPARATION_OF_DUTIES_CONFLICT:%',conflict_rule using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_phase5e_binding_sod on rbac_principal_role_bindings;
create trigger trg_phase5e_binding_sod before insert or update on rbac_principal_role_bindings
for each row execute function phase5e_validate_separation_of_duties();

create or replace function phase1a4_validate_principal_binding()
returns trigger language plpgsql as $$
declare role_tenant varchar(64); role_kind varchar(40); role_status varchar(32); role_code varchar(128); role_risk varchar(16);
begin
  select tenant_id,role_type,status,rbac_roles.role_code,risk_level into role_tenant,role_kind,role_status,role_code,role_risk
    from rbac_roles where role_id=new.role_id for key share;
  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_status<>'ACTIVE' then raise exception 'ROLE_DISABLED' using errcode='23514'; end if;
  if role_tenant is not null and role_tenant is distinct from new.tenant_id then raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514'; end if;
  if role_kind in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE') and (new.scope_type<>'INSTANCE' or new.scope_id<>'INSTANCE' or new.tenant_id is not null) then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_kind not in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE') and new.scope_type='INSTANCE' then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_code in('SYSTEM_ADMIN','TENANT_ADMIN') and new.principal_type<>'USER' then
    raise exception 'ROLE_BINDING_PRINCIPAL_FORBIDDEN' using errcode='23514';
  end if;
  if new.principal_type='SERVICE_ACCOUNT' or role_risk='CRITICAL' then
    new.review_required=true;
    new.next_review_at=coalesce(new.next_review_at,now()+case when role_risk='CRITICAL' then interval '30 days' else interval '90 days' end);
  end if;
  return new;
end $$;

create or replace function phase5e_rbac_evidence()
returns trigger language plpgsql as $$
declare old_json jsonb:='{}'::jsonb; new_json jsonb:='{}'::jsonb; target_tenant varchar(64); target_id varchar(128); target_role varchar(128); ptype varchar(40); pid varchar(128); perm varchar(160); stype varchar(32); sid varchar(128); event_name varchar(64); actor varchar(128); reason varchar(500);
begin
  if tg_op<>'INSERT' then old_json=to_jsonb(old); end if;
  if tg_op<>'DELETE' then new_json=to_jsonb(new); end if;
  reason=coalesce(nullif(current_setting('app.current_audit_reason',true),''),'Database-enforced RBAC administration mutation');
  if tg_table_name='rbac_roles' then
    target_tenant=case when tg_op='DELETE' then old.tenant_id else new.tenant_id end;
    target_id=case when tg_op='DELETE' then old.role_id else new.role_id end;
    target_role=target_id;
    actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),case when tg_op='DELETE' then old.updated_by else new.updated_by end,case when tg_op='DELETE' then old.created_by else new.created_by end,'phase5e-system');
    event_name='ROLE_'||tg_op;
  elsif tg_table_name='rbac_role_permissions' then
    target_tenant=case when tg_op='DELETE' then old.tenant_id else new.tenant_id end;
    target_id=case when tg_op='DELETE' then old.grant_id else new.grant_id end;
    target_role=case when tg_op='DELETE' then old.role_id else new.role_id end;
    perm=case when tg_op='DELETE' then old.permission_point else new.permission_point end;
    actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),case when tg_op='DELETE' then old.created_by else new.created_by end,'phase5e-system');
    event_name='ROLE_PERMISSION_'||tg_op;
  else
    target_tenant=case when tg_op='DELETE' then old.tenant_id else new.tenant_id end;
    target_id=case when tg_op='DELETE' then old.binding_id else new.binding_id end;
    target_role=case when tg_op='DELETE' then old.role_id else new.role_id end;
    ptype=case when tg_op='DELETE' then old.principal_type else new.principal_type end;
    pid=case when tg_op='DELETE' then old.principal_id else new.principal_id end;
    stype=case when tg_op='DELETE' then old.scope_type else new.scope_type end;
    sid=case when tg_op='DELETE' then old.scope_id else new.scope_id end;
    actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),case when tg_op='DELETE' then old.revoked_by else new.revoked_by end,case when tg_op='DELETE' then old.created_by else new.created_by end,'phase5e-system');
    event_name='ROLE_BINDING_'||tg_op;
  end if;
  insert into rbac_administration_events(event_id,tenant_id,event_type,aggregate_type,aggregate_id,principal_type,principal_id,role_id,permission_code,scope_type,scope_id,previous_json,current_json,audit_reason,actor_id,correlation_id,occurred_at)
  values(gen_random_uuid()::text,target_tenant,event_name,tg_table_name,target_id,ptype,pid,target_role,perm,stype,sid,old_json,new_json,reason,actor,nullif(current_setting('app.current_correlation_id',true),''),now());
  if tg_op='DELETE' then return old; end if;return new;
end $$;

drop trigger if exists trg_phase5e_role_evidence on rbac_roles;
create trigger trg_phase5e_role_evidence after insert or update or delete on rbac_roles for each row execute function phase5e_rbac_evidence();
drop trigger if exists trg_phase5e_role_permission_evidence on rbac_role_permissions;
create trigger trg_phase5e_role_permission_evidence after insert or update or delete on rbac_role_permissions for each row execute function phase5e_rbac_evidence();
drop trigger if exists trg_phase5e_binding_evidence on rbac_principal_role_bindings;
create trigger trg_phase5e_binding_evidence after insert or update or delete on rbac_principal_role_bindings for each row execute function phase5e_rbac_evidence();

create or replace function phase5e_reject_admin_event_mutation()
returns trigger language plpgsql as $$ begin raise exception 'RBAC_ADMINISTRATION_EVENT_APPEND_ONLY' using errcode='55000'; end $$;
drop trigger if exists trg_phase5e_admin_event_immutable on rbac_administration_events;
create trigger trg_phase5e_admin_event_immutable before update or delete on rbac_administration_events
for each row execute function phase5e_reject_admin_event_mutation();
