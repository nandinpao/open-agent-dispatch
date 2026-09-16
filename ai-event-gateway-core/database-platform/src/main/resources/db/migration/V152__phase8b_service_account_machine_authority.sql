-- Phase 8B: Service Account Machine Authority & Credential Binding.
-- Human IAM/RBAC stays frozen. This migration adds canonical machine boundaries and long-lived
-- client credentials that will be exchanged for short-lived access tokens in Phase 8C.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase8b-machine-authority',true);

alter table token_service_accounts
  add column if not exists machine_scopes varchar(160)[] not null default '{}',
  add column if not exists allowed_source_systems varchar(160)[] not null default '{}',
  add column if not exists credential_max_ttl_seconds bigint not null default 15552000,
  add column if not exists max_active_credentials integer not null default 2;

alter table token_service_accounts drop constraint if exists ck_phase8b_service_credential_policy;
alter table token_service_accounts add constraint ck_phase8b_service_credential_policy
  check(credential_max_ttl_seconds between 60 and 31536000 and max_active_credentials between 1 and 10);

create table if not exists token_service_account_credentials (
  tenant_id varchar(64) not null,
  credential_id varchar(128) not null,
  service_account_id varchar(128) not null,
  credential_type varchar(32) not null default 'CLIENT_SECRET',
  credential_name varchar(160) not null,
  client_id varchar(96) not null,
  last4 varchar(4) not null,
  hash_algorithm varchar(32) not null,
  secret_hash varchar(512) not null,
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  last_used_at timestamptz,
  rotated_from_credential_id varchar(128),
  rotation_grace_expires_at timestamptz,
  revoked_at timestamptz,
  revoked_by varchar(128),
  revocation_reason varchar(500) not null default '',
  status varchar(24) not null default 'ACTIVE',
  use_count bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by varchar(128) not null,
  updated_by varchar(128) not null,
  version bigint not null default 1,
  primary key(tenant_id,credential_id),
  unique(client_id),
  foreign key(tenant_id,service_account_id) references token_service_accounts(tenant_id,service_account_id),
  foreign key(tenant_id,rotated_from_credential_id) references token_service_account_credentials(tenant_id,credential_id),
  constraint ck_phase8b_credential_type check(credential_type in('CLIENT_SECRET')),
  constraint ck_phase8b_credential_status check(status in('ACTIVE','ROTATING','REVOKED')),
  constraint ck_phase8b_credential_time check(expires_at>issued_at and (rotation_grace_expires_at is null or (rotation_grace_expires_at>=issued_at and rotation_grace_expires_at<=expires_at))),
  constraint ck_phase8b_credential_last4 check(length(last4)=4),
  constraint ck_phase8b_credential_secret_storage check(hash_algorithm='HMAC-SHA-256' and length(secret_hash)>=32 and secret_hash not like 'odp\_%'),
  constraint ck_phase8b_credential_client_id check(client_id ~ '^odp_sac_[0-9a-f]{18}$'),
  constraint ck_phase8b_credential_rotation_origin check(rotated_from_credential_id is null or rotated_from_credential_id<>credential_id),
  constraint ck_phase8b_credential_state_evidence check((status<>'REVOKED' and revoked_at is null) or (status='REVOKED' and revoked_at is not null)),
  constraint ck_phase8b_credential_rotation_evidence check(status<>'ROTATING' or rotation_grace_expires_at is not null),
  constraint ck_phase8b_credential_use_count check(use_count>=0)
);
create index if not exists idx_phase8b_credential_service
  on token_service_account_credentials(tenant_id,service_account_id,status,expires_at);
create index if not exists idx_phase8b_credential_last_used
  on token_service_account_credentials(tenant_id,last_used_at desc);

alter table token_service_account_credentials enable row level security;
alter table token_service_account_credentials force row level security;
drop policy if exists token_tenant_isolation on token_service_account_credentials;
create policy token_tenant_isolation on token_service_account_credentials
  using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

-- Public client_id -> Tenant resolution directory. It contains no secret material and is intentionally
-- outside Tenant RLS so the Phase 8C token endpoint can resolve the Tenant before opening a Tenant transaction.
create table if not exists iam_machine_credential_directory (
  client_id varchar(96) primary key,
  tenant_id varchar(64) not null references tenants(tenant_id),
  credential_id varchar(128) not null,
  service_account_id varchar(128) not null,
  credential_type varchar(32) not null,
  status varchar(24) not null,
  expires_at timestamptz not null,
  updated_at timestamptz not null
);
create index if not exists idx_phase8b_machine_credential_directory_tenant
  on iam_machine_credential_directory(tenant_id,service_account_id,status,expires_at);

create or replace function phase8b_refresh_machine_credential_directory()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  insert into iam_machine_credential_directory(
    client_id,tenant_id,credential_id,service_account_id,credential_type,status,expires_at,updated_at)
  values(new.client_id,new.tenant_id,new.credential_id,new.service_account_id,new.credential_type,new.status,new.expires_at,now())
  on conflict(client_id) do update set
    tenant_id=excluded.tenant_id,
    credential_id=excluded.credential_id,
    service_account_id=excluded.service_account_id,
    credential_type=excluded.credential_type,
    status=excluded.status,
    expires_at=excluded.expires_at,
    updated_at=excluded.updated_at;
  return new;
end $$;
revoke all on function phase8b_refresh_machine_credential_directory() from public;
drop trigger if exists trg_phase8b_machine_credential_directory on token_service_account_credentials;
create trigger trg_phase8b_machine_credential_directory
 after insert or update of status,expires_at on token_service_account_credentials
 for each row execute function phase8b_refresh_machine_credential_directory();

create or replace function phase8b_validate_service_account_credential()
returns trigger language plpgsql as $$
declare
  account_status varchar(32);
  owner_user varchar(128);
  owner_department varchar(128);
  review_at timestamptz;
  max_ttl bigint;
  max_active integer;
  active_count integer;
  owner_active integer;
  department_active integer;
begin
  if not exists(select 1 from tenants where tenant_id=new.tenant_id and status='ACTIVE') then
    raise exception 'AUTH_TOKEN_TENANT_MISMATCH' using errcode='23514';
  end if;
  select status,owner_user_id,owner_department_id,next_review_at,credential_max_ttl_seconds,max_active_credentials
    into account_status,owner_user,owner_department,review_at,max_ttl,max_active
    from token_service_accounts
   where tenant_id=new.tenant_id and service_account_id=new.service_account_id
   for key share;
  if not found then raise exception 'SERVICE_ACCOUNT_NOT_FOUND' using errcode='23514'; end if;
  if account_status<>'ACTIVE' or review_at<=now() then
    raise exception 'SERVICE_ACCOUNT_OWNERSHIP_REVIEW_REQUIRED' using errcode='23514';
  end if;
  select count(*) into owner_active
    from iam_users u
    join org_tenant_memberships m
      on m.user_id=u.user_id and m.tenant_id=new.tenant_id and m.status='ACTIVE'
    join org_department_memberships dm
      on dm.tenant_id=new.tenant_id and dm.user_id=u.user_id
     and dm.department_id=owner_department and dm.status='ACTIVE'
     and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now())
   where u.user_id=owner_user and u.status='ACTIVE';
  if owner_active<>1 then
    raise exception 'SERVICE_ACCOUNT_OWNER_INACTIVE' using errcode='23514';
  end if;
  select count(*) into department_active
    from departments d
   where d.tenant_id=new.tenant_id and d.department_id=owner_department and d.status='ACTIVE';
  if department_active<>1 then
    raise exception 'SERVICE_ACCOUNT_OWNER_INACTIVE' using errcode='23514';
  end if;
  if extract(epoch from(new.expires_at-new.issued_at))<60 or extract(epoch from(new.expires_at-new.issued_at))>max_ttl then
    raise exception 'SERVICE_ACCOUNT_CREDENTIAL_TTL_EXCEEDED' using errcode='23514';
  end if;
  select count(*) into active_count
    from token_service_account_credentials
   where tenant_id=new.tenant_id and service_account_id=new.service_account_id
     and status in('ACTIVE','ROTATING') and expires_at>now()
     and (status='ACTIVE' or rotation_grace_expires_at>now())
     and credential_id<>new.credential_id;
  if new.rotated_from_credential_id is null and active_count>=max_active then
    raise exception 'SERVICE_ACCOUNT_CREDENTIAL_ACTIVE_LIMIT_EXCEEDED' using errcode='23514';
  end if;
  if new.rotated_from_credential_id is not null and active_count>max_active then
    raise exception 'SERVICE_ACCOUNT_CREDENTIAL_ACTIVE_LIMIT_EXCEEDED' using errcode='23514';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase8b_validate_service_account_credential on token_service_account_credentials;
create trigger trg_phase8b_validate_service_account_credential
 before insert on token_service_account_credentials
 for each row execute function phase8b_validate_service_account_credential();

create or replace function phase8b_protect_service_account_credential()
returns trigger language plpgsql as $$
begin
  if new.tenant_id is distinct from old.tenant_id
     or new.credential_id is distinct from old.credential_id
     or new.service_account_id is distinct from old.service_account_id
     or new.credential_type is distinct from old.credential_type
     or new.credential_name is distinct from old.credential_name
     or new.client_id is distinct from old.client_id
     or new.last4 is distinct from old.last4
     or new.hash_algorithm is distinct from old.hash_algorithm
     or new.secret_hash is distinct from old.secret_hash
     or new.issued_at is distinct from old.issued_at
     or new.expires_at is distinct from old.expires_at
     or new.rotated_from_credential_id is distinct from old.rotated_from_credential_id
     or new.created_at is distinct from old.created_at
     or new.created_by is distinct from old.created_by then
    raise exception 'SERVICE_ACCOUNT_CREDENTIAL_IMMUTABLE_FIELDS' using errcode='55000';
  end if;
  if old.status='REVOKED' and new is distinct from old then
    raise exception 'SERVICE_ACCOUNT_CREDENTIAL_REVOKED' using errcode='55000';
  end if;
  if new.status='ROTATING' and (old.status<>'ACTIVE' or new.rotation_grace_expires_at is null) then
    raise exception 'SERVICE_ACCOUNT_CREDENTIAL_ROTATION_CONFLICT' using errcode='55000';
  end if;
  if old.status='ROTATING' and new.status not in('ROTATING','REVOKED') then
    raise exception 'SERVICE_ACCOUNT_CREDENTIAL_ROTATION_CONFLICT' using errcode='55000';
  end if;
  if new.version<>old.version+1 then raise exception 'IDENTITY_VERSION_CONFLICT' using errcode='40001'; end if;
  return new;
end $$;
drop trigger if exists trg_phase8b_protect_service_account_credential on token_service_account_credentials;
create trigger trg_phase8b_protect_service_account_credential
 before update on token_service_account_credentials
 for each row execute function phase8b_protect_service_account_credential();

-- Machine boundary changes invalidate all pre-change Service Account access tokens through the existing security epoch.
drop trigger if exists trg_phase1a5_service_account_epoch on token_service_accounts;
create trigger trg_phase1a5_service_account_epoch
 after update of owner_user_id,owner_department_id,permission_scopes,allowed_audiences,allowed_api_prefixes,allowed_cidrs,
                 machine_scopes,allowed_source_systems,credential_max_ttl_seconds,max_active_credentials,status,risk_level
 on token_service_accounts for each row execute function phase1a5_service_account_security_change();

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('SERVICE_ACCOUNT_CREDENTIAL_NOT_FOUND',404,'VALIDATION',false,'The Service Account credential was not found.'),
 ('SERVICE_ACCOUNT_CREDENTIAL_INVALID',401,'AUTHENTICATION',false,'The Service Account credential is invalid.'),
 ('SERVICE_ACCOUNT_CREDENTIAL_EXPIRED',401,'AUTHENTICATION',false,'The Service Account credential has expired.'),
 ('SERVICE_ACCOUNT_CREDENTIAL_REVOKED',401,'AUTHENTICATION',false,'The Service Account credential was revoked.'),
 ('SERVICE_ACCOUNT_CREDENTIAL_TTL_EXCEEDED',400,'VALIDATION',false,'The requested Service Account credential lifetime exceeds policy.'),
 ('SERVICE_ACCOUNT_CREDENTIAL_ACTIVE_LIMIT_EXCEEDED',409,'SECURITY',false,'The active Service Account credential limit was reached.'),
 ('SERVICE_ACCOUNT_CREDENTIAL_ROTATION_CONFLICT',409,'SECURITY',false,'The Service Account credential cannot be rotated in its current state.'),
 ('IAM_SECRET_RESPONSE_NOT_REPLAYABLE',409,'SECURITY',false,'The secret was already issued and cannot be displayed again; rotate the credential to create a new secret.')
on conflict(reason_code) do update set
 http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,
 message_template=excluded.message_template,active=true;

insert into domain_event_catalog(event_type,payload_version,aggregate_type,description,exportable)
values
 ('SERVICE_ACCOUNT_MACHINE_BOUNDARY_UPDATED','1','SERVICE_ACCOUNT','A Service Account machine authority boundary was updated.',false),
 ('SERVICE_ACCOUNT_CREDENTIAL_CREATED','1','SERVICE_ACCOUNT_CREDENTIAL','A Service Account client credential was created.',false),
 ('SERVICE_ACCOUNT_CREDENTIAL_ROTATED','1','SERVICE_ACCOUNT_CREDENTIAL','A Service Account client credential was rotated.',false),
 ('SERVICE_ACCOUNT_CREDENTIAL_REVOKED','1','SERVICE_ACCOUNT_CREDENTIAL','A Service Account client credential was revoked.',false),
 ('SERVICE_ACCOUNT_CREDENTIAL_USED','1','SERVICE_ACCOUNT_CREDENTIAL','A Service Account client credential was validated and used.',false)
on conflict(event_type,payload_version) do nothing;

-- Canonical Human-admin route authority. These routes remain Human IAM administration APIs;
-- only the future token-exchange endpoint will be NON_HUMAN.
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
 ('REST:POST:/api/admin/access/security/service-accounts/{id}/machine-boundary','REST','control-plane-app','iam-api','IamTokenController.updateMachineBoundary','/api/admin/access/security/service-accounts/{id}/machine-boundary','POST','TARGET_ONLY','security.token.manage',null,'[]'::jsonb,'IAM_TOKEN','R3_PATH_RESOURCE_RESOLVER',null,null,'phase8b-machine-authority-2026-08-13','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamTokenController.java#updateMachineBoundary','9fd82b62a9cc417830bc2e6fedfc049157c2af122bb9b0644fa0a873bcf32ae7',now(),'phase8b-machine-authority','phase8b-machine-authority'),
 ('REST:GET:/api/admin/access/security/service-accounts/{id}/credentials','REST','control-plane-app','iam-api','IamTokenController.credentials','/api/admin/access/security/service-accounts/{id}/credentials','GET','TARGET_ONLY','security.token.read',null,'[]'::jsonb,'IAM_TOKEN','R3_PATH_RESOURCE_RESOLVER',null,null,'phase8b-machine-authority-2026-08-13','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamTokenController.java#credentials','9fd82b62a9cc417830bc2e6fedfc049157c2af122bb9b0644fa0a873bcf32ae7',now(),'phase8b-machine-authority','phase8b-machine-authority'),
 ('REST:POST:/api/admin/access/security/service-accounts/{id}/credentials','REST','control-plane-app','iam-api','IamTokenController.issueCredential','/api/admin/access/security/service-accounts/{id}/credentials','POST','TARGET_ONLY','security.token.manage',null,'[]'::jsonb,'IAM_TOKEN','R3_PATH_RESOURCE_RESOLVER',null,null,'phase8b-machine-authority-2026-08-13','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamTokenController.java#issueCredential','9fd82b62a9cc417830bc2e6fedfc049157c2af122bb9b0644fa0a873bcf32ae7',now(),'phase8b-machine-authority','phase8b-machine-authority'),
 ('REST:POST:/api/admin/access/security/service-accounts/{id}/credentials/{credentialId}/rotate','REST','control-plane-app','iam-api','IamTokenController.rotateCredential','/api/admin/access/security/service-accounts/{id}/credentials/{credentialId}/rotate','POST','TARGET_ONLY','security.token.manage',null,'[]'::jsonb,'IAM_TOKEN','R3_PATH_RESOURCE_RESOLVER',null,null,'phase8b-machine-authority-2026-08-13','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamTokenController.java#rotateCredential','9fd82b62a9cc417830bc2e6fedfc049157c2af122bb9b0644fa0a873bcf32ae7',now(),'phase8b-machine-authority','phase8b-machine-authority'),
 ('REST:POST:/api/admin/access/security/service-accounts/{id}/credentials/{credentialId}/revoke','REST','control-plane-app','iam-api','IamTokenController.revokeCredential','/api/admin/access/security/service-accounts/{id}/credentials/{credentialId}/revoke','POST','TARGET_ONLY','security.token.manage',null,'[]'::jsonb,'IAM_TOKEN','R3_PATH_RESOURCE_RESOLVER',null,null,'phase8b-machine-authority-2026-08-13','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamTokenController.java#revokeCredential','9fd82b62a9cc417830bc2e6fedfc049157c2af122bb9b0644fa0a873bcf32ae7',now(),'phase8b-machine-authority','phase8b-machine-authority')
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state='TARGET_ONLY',target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='phase8b-machine-authority',
 version=permission_entry_point_inventory.version+1;
