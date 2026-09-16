-- v24 Phase 2: resolve opaque PATs to their authoritative Tenant without trusting X-Tenant-Id.
-- This global directory intentionally carries the minimum lookup metadata only. The token ID,
-- principal ID, secret/hash, scope and security epochs remain exclusively in the FORCE-RLS
-- token_access_tokens table and are validated after the authoritative Tenant context is opened.
create table if not exists iam_personal_access_token_directory (
  token_prefix varchar(96) primary key,
  tenant_id varchar(64) not null references tenants(tenant_id),
  token_type varchar(48) not null,
  expires_at timestamptz not null,
  status varchar(32) not null,
  updated_at timestamptz not null default now(),
  constraint ck_pat_directory_type check (token_type='PERSONAL_ACCESS_TOKEN')
);
create index if not exists idx_pat_directory_expiry on iam_personal_access_token_directory(status,expires_at);

-- Backfill through each Tenant because token_access_tokens is FORCE RLS. The migration authority
-- may set transaction-local Tenant context but does not disable RLS or infer Tenant from token data.
do $$
declare
  v_tenant_id varchar(64);
  v_previous_tenant text := current_setting('app.current_tenant_id', true);
begin
  for v_tenant_id in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id', v_tenant_id, true);
    insert into iam_personal_access_token_directory(token_prefix,tenant_id,token_type,expires_at,status,updated_at)
    select token_prefix,tenant_id,token_type,expires_at,status,now()
      from token_access_tokens
     where tenant_id=v_tenant_id and token_type='PERSONAL_ACCESS_TOKEN'
    on conflict(token_prefix) do update set
      tenant_id=excluded.tenant_id, token_type=excluded.token_type,
      expires_at=excluded.expires_at, status=excluded.status, updated_at=excluded.updated_at;
  end loop;
  perform set_config('app.current_tenant_id', coalesce(v_previous_tenant,''), true);
end $$;

-- token_access_tokens is authoritative. This SECURITY DEFINER trigger only mirrors minimum routing
-- metadata into the non-RLS directory; it cannot grant authorization and exposes no subject identity.
create or replace function v24_phase2_refresh_pat_directory() returns trigger
language plpgsql security definer set search_path=pg_catalog,public as $$
begin
  if new.token_type='PERSONAL_ACCESS_TOKEN' then
    insert into public.iam_personal_access_token_directory(token_prefix,tenant_id,token_type,expires_at,status,updated_at)
    values(new.token_prefix,new.tenant_id,new.token_type,new.expires_at,new.status,now())
    on conflict(token_prefix) do update set
      tenant_id=excluded.tenant_id, token_type=excluded.token_type,
      expires_at=excluded.expires_at, status=excluded.status, updated_at=excluded.updated_at;
  end if;
  return new;
end $$;

drop trigger if exists trg_v24_phase2_pat_directory on token_access_tokens;
create trigger trg_v24_phase2_pat_directory
after insert or update of status,expires_at on token_access_tokens
for each row execute function v24_phase2_refresh_pat_directory();
revoke all on function v24_phase2_refresh_pat_directory() from public;

revoke all on iam_personal_access_token_directory from public;
revoke insert,update,delete on iam_personal_access_token_directory from opendispatch_runtime;
grant select on iam_personal_access_token_directory to opendispatch_runtime;
