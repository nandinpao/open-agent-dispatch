-- RS1: permission-neutral Resource Scope Share persistence.
-- A share expands resource discoverability to an organizational scope. It never grants a Permission.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rs1-resource-scope-share',true);

create table if not exists resource_scope_shares (
  tenant_id varchar(64) not null,
  share_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  target_scope_type varchar(32) not null,
  target_scope_id varchar(128) not null,
  share_reason text not null,
  valid_from timestamptz not null,
  valid_to timestamptz,
  share_state varchar(24) not null default 'ACTIVE',
  idempotency_key varchar(256) not null,
  created_by varchar(128) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  revoked_by varchar(128),
  revoked_at timestamptz,
  version bigint not null default 1,
  primary key (tenant_id, share_id),
  unique (tenant_id, idempotency_key),
  foreign key (tenant_id) references tenants(tenant_id),
  foreign key (resource_type) references resource_catalog(resource_type),
  foreign key (tenant_id,resource_type,resource_id) references resource_descriptors(tenant_id,resource_type,resource_id),
  check (target_scope_type in ('DEPARTMENT','DEPARTMENT_SUBTREE','GROUP')),
  check (share_state in ('ACTIVE','REVOKED')),
  check (valid_to is null or valid_to > valid_from),
  check (version > 0),
  check (share_state <> 'REVOKED' or (nullif(revoked_by,'') is not null and revoked_at is not null))
);

create index if not exists idx_resource_scope_shares_resource
  on resource_scope_shares(tenant_id,resource_type,resource_id,share_state,valid_from,valid_to);
create index if not exists idx_resource_scope_shares_target
  on resource_scope_shares(tenant_id,target_scope_type,target_scope_id,resource_type,share_state,valid_from,valid_to);

-- Tenant hard boundary. Scope Share is Tenant-owned authorization evidence and must fail closed without transaction context.
alter table resource_scope_shares enable row level security;
alter table resource_scope_shares force row level security;
drop policy if exists tenant_isolation on resource_scope_shares;
create policy tenant_isolation on resource_scope_shares
  using (tenant_id = iam_current_tenant_id())
  with check (tenant_id = iam_current_tenant_id());

create or replace function rs1_validate_resource_scope_share()
returns trigger language plpgsql security invoker as $$
begin
  if new.target_scope_type in ('DEPARTMENT','DEPARTMENT_SUBTREE') then
    if not exists(select 1 from departments d where d.tenant_id=new.tenant_id and d.department_id=new.target_scope_id and d.status='ACTIVE') then
      raise exception 'RESOURCE_SCOPE_SHARE_DEPARTMENT_INVALID' using errcode='23514';
    end if;
  elsif new.target_scope_type='GROUP' then
    if not exists(select 1 from organization_groups g where g.tenant_id=new.tenant_id and g.group_id=new.target_scope_id and g.status='ACTIVE') then
      raise exception 'RESOURCE_SCOPE_SHARE_GROUP_INVALID' using errcode='23514';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_rs1_resource_scope_share_validate on resource_scope_shares;
create trigger trg_rs1_resource_scope_share_validate
before insert or update of tenant_id,target_scope_type,target_scope_id,share_state on resource_scope_shares
for each row execute function rs1_validate_resource_scope_share();

create or replace function rs1_touch_resource_scope_share_epoch()
returns trigger language plpgsql as $$
declare t varchar; rt varchar; rid varchar; actor varchar;
begin
  if tg_op='DELETE' then t=old.tenant_id;rt=old.resource_type;rid=old.resource_id; else t=new.tenant_id;rt=new.resource_type;rid=new.resource_id; end if;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs1-scope-share');
  perform p4ra_advance_policy_revision(t,actor);
  insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  values(t,rt,rid,1,now(),actor)
  on conflict(tenant_id,resource_type,resource_id) do update
    set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,
        updated_at=excluded.updated_at,updated_by=excluded.updated_by;
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_rs1_resource_scope_share_epoch on resource_scope_shares;
create trigger trg_rs1_resource_scope_share_epoch
after insert or update or delete on resource_scope_shares
for each row execute function rs1_touch_resource_scope_share_epoch();

comment on table resource_scope_shares is 'RS1 permission-neutral resource discoverability shares. Authorization still requires an independent IAM permission/Role Binding scope.';
comment on column resource_scope_shares.target_scope_type is 'Only DEPARTMENT, DEPARTMENT_SUBTREE, GROUP. TENANT sharing is represented by primary Tenant ownership, not a share.';
