-- R2 Canonical Identity & Session Unification.
-- Credential providers identify a single canonical IAM subject. Provider metadata
-- is authentication evidence only and never an authorization source.

create table if not exists iam_identity_credential_links (
  credential_link_id varchar(128) primary key,
  provider_type varchar(48) not null,
  provider_subject varchar(320) not null,
  provider_external_id varchar(128),
  normalized_provider_subject varchar(320) not null,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  canonical_username varchar(128) not null,
  status varchar(32) not null default 'ACTIVE',
  replacement_required boolean not null default false,
  source_reference varchar(512),
  last_authenticated_at timestamptz,
  replaced_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  version bigint not null default 1,
  constraint uq_iam_identity_credential_provider_subject unique(provider_type, normalized_provider_subject),
  constraint ck_iam_identity_credential_provider check(provider_type in ('IAM_PASSWORD','LEGACY_PASSWORD','OIDC','SAML','PASSKEY','RECOVERY')),
  constraint ck_iam_identity_credential_subject_type check(subject_type in ('INSTANCE_ROOT','HUMAN_USER')),
  constraint ck_iam_identity_credential_status check(status in ('ACTIVE','REPLACED','DISABLED')),
  constraint ck_iam_identity_credential_replaced check(
    (status='REPLACED' and replaced_at is not null) or status<>'REPLACED')
);
create index if not exists idx_iam_identity_credential_subject
  on iam_identity_credential_links(subject_type,subject_id,status);

insert into iam_identity_credential_links(
  credential_link_id,provider_type,provider_subject,provider_external_id,normalized_provider_subject,
  subject_type,subject_id,canonical_username,status,replacement_required,
  source_reference,created_at,updated_at,updated_by,version)
select 'iam-password:user:'||u.user_id,'IAM_PASSWORD',u.username,u.user_id,u.normalized_username,
       'HUMAN_USER',u.user_id,u.username,'ACTIVE',false,
       'V110:IAM_USER_BACKFILL',now(),now(),'r2-canonical-session-migration',1
from iam_users u
on conflict(provider_type,normalized_provider_subject) do nothing;

insert into iam_identity_credential_links(
  credential_link_id,provider_type,provider_subject,provider_external_id,normalized_provider_subject,
  subject_type,subject_id,canonical_username,status,replacement_required,
  source_reference,created_at,updated_at,updated_by,version)
select 'iam-password:root','IAM_PASSWORD','root','root','root','INSTANCE_ROOT','root','root',
       'ACTIVE',false,'V110:ROOT_BACKFILL',now(),now(),'r2-canonical-session-migration',1
where exists(select 1 from iam_root_identities where root_identity_id='root')
on conflict(provider_type,normalized_provider_subject) do nothing;

-- Only migration rows that already identify a concrete target IAM user are linked.
-- No password hash or plaintext credential is copied into the IAM schema.
insert into iam_identity_credential_links(
  credential_link_id,provider_type,provider_subject,provider_external_id,normalized_provider_subject,
  subject_type,subject_id,canonical_username,status,replacement_required,
  source_reference,created_at,updated_at,updated_by,version)
select 'legacy-password:'||m.migration_id,'LEGACY_PASSWORD',m.legacy_username,m.legacy_user_id,lower(trim(m.legacy_username)),
       'HUMAN_USER',m.target_user_id,u.username,'ACTIVE',true,
       'iam_legacy_identity_migrations:'||m.migration_id,coalesce(m.created_at,now()),now(),
       'r2-canonical-session-migration',1
from iam_legacy_identity_migrations m
join iam_users u on u.user_id=m.target_user_id
where m.migration_status in ('MIGRATED','MIGRATING','PLANNED')
on conflict(provider_type,normalized_provider_subject) do nothing;

-- Runtime can authenticate and advance migration state, but cannot create arbitrary links.
grant select on iam_identity_credential_links to opendispatch_runtime;
grant update(last_authenticated_at,status,replaced_at,updated_at,updated_by,version)
  on iam_identity_credential_links to opendispatch_runtime;


-- R2 canonical one-time authentication token scope.
-- Existing short-lived one-time tokens are invalidated at cutover rather than accepted
-- against a removed browser endpoint. Newly issued tokens are restricted to /api/session/.
-- token_access_tokens has FORCE RLS from V32. Revoke legacy endpoint tokens one
-- Tenant at a time with transaction-local context; never disable RLS or grant BYPASSRLS.
do $$
declare
  v_tenant_id varchar(64);
  v_previous_tenant text := current_setting('app.current_tenant_id', true);
  v_previous_actor text := current_setting('app.current_actor_id', true);
begin
  for v_tenant_id in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id', v_tenant_id, true);
    perform set_config('app.current_actor_id', 'r2-canonical-session-migration', true);

    update token_access_tokens
       set status='REVOKED',
           revoked_at=coalesce(revoked_at,now()),
           revoked_by='r2-canonical-session-migration',
           revocation_reason='R2_CANONICAL_SESSION_ENDPOINT_CUTOVER',
           version=version+1
     where tenant_id=v_tenant_id
       and token_type in ('INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN')
       and status in ('ACTIVE','ROTATING')
       and api_prefixes @> array['/api/auth/']::varchar[];

    if exists(
      select 1
        from token_access_tokens
       where tenant_id=v_tenant_id
         and token_type in ('INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN')
         and status in ('ACTIVE','ROTATING')
         and api_prefixes @> array['/api/auth/']::varchar[]
    ) then
      raise exception 'R2_LEGACY_AUTH_TOKEN_REVOCATION_INCOMPLETE' using errcode='23514';
    end if;
  end loop;

  perform set_config('app.current_tenant_id', coalesce(v_previous_tenant,''), true);
  perform set_config('app.current_actor_id', coalesce(v_previous_actor,''), true);
exception when others then
  perform set_config('app.current_tenant_id', coalesce(v_previous_tenant,''), true);
  perform set_config('app.current_actor_id', coalesce(v_previous_actor,''), true);
  raise;
end $$;

create or replace function phase1a5_validate_token()
returns trigger language plpgsql as $$
declare
  principal_active integer;
  service_status varchar(32);
  service_max_ttl bigint;
  service_max_active integer;
  service_next_review timestamptz;
  service_permissions varchar(160)[];
  service_audiences varchar(160)[];
  service_api_prefixes varchar(256)[];
  service_cidrs varchar(80)[];
  active_count integer;
  personal_max_ttl bigint;
  personal_max_active integer;
begin
  if not exists(select 1 from tenants where tenant_id=new.tenant_id and status='ACTIVE') then
    raise exception 'AUTH_TOKEN_TENANT_MISMATCH' using errcode='23514';
  end if;
  if new.principal_type='USER' then
    if new.token_type='SERVICE_ACCOUNT_TOKEN' then
      raise exception 'AUTH_TOKEN_TYPE_MISMATCH' using errcode='23514';
    end if;
    select count(*) into principal_active
      from iam_users u
      join org_tenant_memberships m on m.user_id=u.user_id and m.tenant_id=new.tenant_id
     where u.user_id=new.principal_id
       and (
         (new.token_type='INVITATION_TOKEN' and u.status in('PENDING_ACTIVATION','PASSWORD_RESET_REQUIRED','MFA_ENROLLMENT_REQUIRED') and m.status in('INVITED','ACTIVE'))
         or (new.token_type='PASSWORD_RESET_TOKEN' and u.status in('ACTIVE','LOCKED','PASSWORD_RESET_REQUIRED','MFA_ENROLLMENT_REQUIRED') and m.status in('ACTIVE','SUSPENDED'))
         or (new.token_type='EMAIL_VERIFICATION_TOKEN' and u.status not in('DELETED','DISABLED') and m.status in('INVITED','ACTIVE'))
         or (new.token_type='PERSONAL_ACCESS_TOKEN' and u.status='ACTIVE' and m.status='ACTIVE')
       );
    if new.token_type in('INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN') then
      if cardinality(new.permission_scopes)<>1 or cardinality(new.audiences)<>1 or not(new.audiences @> array['opendispatch-iam']::varchar[]) or cardinality(new.api_prefixes)<>1 or not(new.api_prefixes @> array['/api/session/']::varchar[]) or cardinality(new.allowed_cidrs)<>0 then
        raise exception 'AUTH_TOKEN_SCOPE_INSUFFICIENT' using errcode='23514';
      end if;
      if (new.token_type='INVITATION_TOKEN' and not(new.permission_scopes @> array['identity.user.activate']::varchar[]))
         or (new.token_type='PASSWORD_RESET_TOKEN' and not(new.permission_scopes @> array['identity.user.password.reset']::varchar[]))
         or (new.token_type='EMAIL_VERIFICATION_TOKEN' and not(new.permission_scopes @> array['identity.user.email.verify']::varchar[])) then
        raise exception 'AUTH_TOKEN_SCOPE_INSUFFICIENT' using errcode='23514';
      end if;
    end if;
    if new.token_type='PERSONAL_ACCESS_TOKEN' then
      if cardinality(new.permission_scopes)=0 or exists(select 1 from unnest(new.permission_scopes) p where p like 'instance.%') then
        raise exception 'AUTH_TOKEN_SCOPE_INSUFFICIENT' using errcode='23514';
      end if;
      select personal_max_ttl_seconds,max_active_personal_tokens
        into personal_max_ttl,personal_max_active
        from token_tenant_policies where tenant_id=new.tenant_id;
      if extract(epoch from(new.expires_at-new.issued_at))>personal_max_ttl then
        raise exception 'AUTH_TOKEN_TTL_EXCEEDED' using errcode='23514';
      end if;
      select count(*) into active_count from token_access_tokens
       where tenant_id=new.tenant_id and principal_type='USER' and principal_id=new.principal_id
         and token_type='PERSONAL_ACCESS_TOKEN' and status in('ACTIVE','ROTATING')
         and expires_at>now() and token_id<>new.token_id;
      if active_count>=personal_max_active then
        raise exception 'AUTH_TOKEN_ACTIVE_LIMIT_EXCEEDED' using errcode='23514';
      end if;
    elsif new.token_type='PASSWORD_RESET_TOKEN' and new.expires_at>new.issued_at+interval '24 hours' then
      raise exception 'AUTH_TOKEN_TTL_EXCEEDED' using errcode='23514';
    elsif new.token_type in('INVITATION_TOKEN','EMAIL_VERIFICATION_TOKEN') and new.expires_at>new.issued_at+interval '7 days' then
      raise exception 'AUTH_TOKEN_TTL_EXCEEDED' using errcode='23514';
    end if;
  else
    if new.token_type<>'SERVICE_ACCOUNT_TOKEN' then
      raise exception 'AUTH_TOKEN_TYPE_MISMATCH' using errcode='23514';
    end if;
    select status,token_max_ttl_seconds,max_active_tokens,next_review_at,permission_scopes,allowed_audiences,allowed_api_prefixes,allowed_cidrs
      into service_status,service_max_ttl,service_max_active,service_next_review,service_permissions,service_audiences,service_api_prefixes,service_cidrs
      from token_service_accounts
     where tenant_id=new.tenant_id and service_account_id=new.principal_id
     for key share;
    if not found or service_status<>'ACTIVE' or service_next_review<=now() then
      raise exception 'SERVICE_ACCOUNT_OWNERSHIP_REVIEW_REQUIRED' using errcode='23514';
    end if;
    if extract(epoch from(new.expires_at-new.issued_at))>service_max_ttl then
      raise exception 'AUTH_TOKEN_TTL_EXCEEDED' using errcode='23514';
    end if;
    if cardinality(new.permission_scopes)=0 or not(new.permission_scopes <@ service_permissions) then
      raise exception 'AUTH_TOKEN_SCOPE_INSUFFICIENT' using errcode='23514';
    end if;
    if cardinality(new.audiences)=0 or not(new.audiences <@ service_audiences) then
      raise exception 'AUTH_TOKEN_AUDIENCE_DENIED' using errcode='23514';
    end if;
    if cardinality(new.api_prefixes)=0 or not(new.api_prefixes <@ service_api_prefixes) then
      raise exception 'AUTH_TOKEN_API_PREFIX_DENIED' using errcode='23514';
    end if;
    if cardinality(new.allowed_cidrs)=0 or not(new.allowed_cidrs <@ service_cidrs) then
      raise exception 'SERVICE_ACCOUNT_CIDR_REQUIRED' using errcode='23514';
    end if;
    if exists(select 1 from unnest(new.permission_scopes) p where p like 'instance.%') then
      raise exception 'SERVICE_ACCOUNT_INSTANCE_PERMISSION_FORBIDDEN' using errcode='23514';
    end if;
    select count(*) into active_count from token_access_tokens
     where tenant_id=new.tenant_id and principal_type='SERVICE_ACCOUNT' and principal_id=new.principal_id
       and token_type='SERVICE_ACCOUNT_TOKEN' and status in('ACTIVE','ROTATING')
       and expires_at>now() and token_id<>new.token_id;
    if active_count>=service_max_active then
      raise exception 'AUTH_TOKEN_ACTIVE_LIMIT_EXCEEDED' using errcode='23514';
    end if;
    select count(*) into principal_active
      from token_service_accounts sa
      join iam_users u on u.user_id=sa.owner_user_id and u.status='ACTIVE'
      join org_tenant_memberships tm on tm.tenant_id=sa.tenant_id and tm.user_id=sa.owner_user_id and tm.status='ACTIVE'
      join departments d on d.tenant_id=sa.tenant_id and d.department_id=sa.owner_department_id and d.status='ACTIVE'
      join org_department_memberships dm on dm.tenant_id=sa.tenant_id and dm.user_id=sa.owner_user_id and dm.department_id=sa.owner_department_id and dm.status='ACTIVE' and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now())
     where sa.tenant_id=new.tenant_id and sa.service_account_id=new.principal_id;
  end if;
  if principal_active<>1 then
    raise exception 'AUTH_TOKEN_INVALID' using errcode='23514';
  end if;
  return new;
end $$;
