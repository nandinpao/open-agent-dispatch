-- Phase 1A-5 Enforcement: constraints, RLS, append-only evidence, owner and token guards.

alter table token_tenant_policies
  add constraint ck_token_policy_ttl check(personal_default_ttl_seconds>0 and personal_default_ttl_seconds<=personal_max_ttl_seconds and personal_max_ttl_seconds<=31536000 and service_default_ttl_seconds>0 and service_default_ttl_seconds<=service_max_ttl_seconds and service_max_ttl_seconds<=7776000),
  add constraint ck_token_policy_limits check(max_active_personal_tokens between 1 and 100 and max_active_service_tokens between 1 and 10),
  add constraint ck_token_policy_version check(version>0);

alter table token_service_accounts
  add constraint ck_token_service_status check(status in('ACTIVE','OWNERSHIP_REVIEW','SUSPENDED_RISK','SUSPENDED','DISABLED','DELETED')),
  add constraint ck_token_service_risk check(risk_level in('LOW','MEDIUM','HIGH','CRITICAL')),
  add constraint ck_token_service_scope check(cardinality(permission_scopes)>0 and cardinality(allowed_audiences)>0 and cardinality(allowed_api_prefixes)>0 and cardinality(allowed_cidrs)>0),
  add constraint ck_token_service_ttl check(token_max_ttl_seconds>0 and token_max_ttl_seconds<=7776000),
  add constraint ck_token_service_limits check(max_active_tokens between 1 and 10 and rate_limit_per_minute>0),
  add constraint ck_token_service_review check(next_review_at>created_at),
  add constraint ck_token_service_version check(version>0);

alter table token_access_tokens
  add constraint ck_token_type check(token_type in('PERSONAL_ACCESS_TOKEN','SERVICE_ACCOUNT_TOKEN','INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN')),
  add constraint ck_token_principal check(principal_type in('USER','SERVICE_ACCOUNT')),
  add constraint ck_token_status check(status in('ACTIVE','ROTATING','REVOKED','EXPIRED','CONSUMED')),
  add constraint ck_token_expiry check(expires_at>issued_at and (rotation_grace_expires_at is null or (rotation_grace_expires_at>issued_at and rotation_grace_expires_at<=expires_at))),
  add constraint ck_token_epochs check(global_security_epoch>=0 and tenant_security_epoch>=0 and principal_security_epoch>=0),
  add constraint ck_token_use_count check(use_count>=0),
  add constraint ck_token_version check(version>0),
  add constraint ck_token_last4 check(length(last4)=4),
  add constraint ck_token_secret_storage check(hash_algorithm='HMAC-SHA-256' and length(token_hash)>=32 and token_hash not like 'odp\_%'),
  add constraint ck_token_prefix_format check(token_prefix ~ '^odp_(pat|sat|invite|reset|verify)_[0-9a-f]{18}$'),
  add constraint ck_token_one_time_consumption check((token_type in('INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN') and (status<>'CONSUMED' or consumed_at is not null)) or token_type in('PERSONAL_ACCESS_TOKEN','SERVICE_ACCOUNT_TOKEN'));

alter table token_usage_events
  add constraint ck_token_usage_outcome check(outcome in('ALLOW','DENY'));
alter table token_rate_limit_windows
  add constraint ck_token_rate_count check(request_count>0);

do $$
declare table_name text;
begin
  foreach table_name in array array['token_tenant_policies','token_service_accounts','token_access_tokens','token_usage_events','token_rate_limit_windows'] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists token_tenant_isolation on %I',table_name);
    execute format('create policy token_tenant_isolation on %I using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

create or replace function phase1a5_validate_service_account()
returns trigger language plpgsql as $$
declare owner_active integer; department_active integer;
begin
  if not exists(select 1 from tenants where tenant_id=new.tenant_id and status='ACTIVE') then raise exception 'AUTH_TOKEN_TENANT_MISMATCH' using errcode='23514'; end if;
  if cardinality(new.permission_scopes)=0 then raise exception 'AUTH_TOKEN_SCOPE_INSUFFICIENT' using errcode='23514'; end if;
  if cardinality(new.allowed_audiences)=0 then raise exception 'AUTH_TOKEN_AUDIENCE_DENIED' using errcode='23514'; end if;
  if cardinality(new.allowed_api_prefixes)=0 then raise exception 'AUTH_TOKEN_API_PREFIX_DENIED' using errcode='23514'; end if;
  if cardinality(new.allowed_cidrs)=0 then raise exception 'SERVICE_ACCOUNT_CIDR_REQUIRED' using errcode='23514'; end if;
  if exists(select 1 from unnest(new.permission_scopes) p where p like 'instance.%') then
    raise exception 'SERVICE_ACCOUNT_INSTANCE_PERMISSION_FORBIDDEN' using errcode='23514';
  end if;
  select count(*) into owner_active from iam_users u join org_tenant_memberships m on m.user_id=u.user_id and m.tenant_id=new.tenant_id and m.status='ACTIVE' join org_department_memberships dm on dm.tenant_id=new.tenant_id and dm.user_id=u.user_id and dm.department_id=new.owner_department_id and dm.status='ACTIVE' and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now()) where u.user_id=new.owner_user_id and u.status='ACTIVE';
  if owner_active<>1 then raise exception 'SERVICE_ACCOUNT_OWNER_INACTIVE' using errcode='23514'; end if;
  select count(*) into department_active from departments d where d.tenant_id=new.tenant_id and d.department_id=new.owner_department_id and d.status='ACTIVE';
  if department_active<>1 then raise exception 'SERVICE_ACCOUNT_OWNER_INACTIVE' using errcode='23514'; end if;
  return new;
end $$;
drop trigger if exists trg_phase1a5_service_account_validate on token_service_accounts;
create trigger trg_phase1a5_service_account_validate before insert or update of owner_user_id,owner_department_id,allowed_cidrs,permission_scopes,allowed_audiences,allowed_api_prefixes,status on token_service_accounts for each row execute function phase1a5_validate_service_account();

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
      if cardinality(new.permission_scopes)<>1 or cardinality(new.audiences)<>1 or not(new.audiences @> array['opendispatch-iam']::varchar[]) or cardinality(new.api_prefixes)<>1 or not(new.api_prefixes @> array['/api/auth/']::varchar[]) or cardinality(new.allowed_cidrs)<>0 then
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
drop trigger if exists trg_phase1a5_token_validate on token_access_tokens;
create trigger trg_phase1a5_token_validate before insert on token_access_tokens
for each row execute function phase1a5_validate_token();

create or replace function phase1a5_protect_token_secret()
returns trigger language plpgsql as $$
begin
  if new.token_hash is distinct from old.token_hash or new.token_prefix is distinct from old.token_prefix or new.last4 is distinct from old.last4 or new.token_type is distinct from old.token_type or new.principal_type is distinct from old.principal_type or new.principal_id is distinct from old.principal_id or new.tenant_id is distinct from old.tenant_id or new.issued_at is distinct from old.issued_at or new.expires_at is distinct from old.expires_at or new.permission_scopes is distinct from old.permission_scopes or new.audiences is distinct from old.audiences or new.api_prefixes is distinct from old.api_prefixes or new.allowed_cidrs is distinct from old.allowed_cidrs then
    raise exception 'AUTH_TOKEN_IMMUTABLE_FIELDS' using errcode='55000';
  end if;
  if old.status in('REVOKED','CONSUMED') and new is distinct from old then
    raise exception 'AUTH_TOKEN_TERMINAL_STATE' using errcode='55000';
  end if;
  if new.status='CONSUMED' and (new.token_type not in('INVITATION_TOKEN','PASSWORD_RESET_TOKEN','EMAIL_VERIFICATION_TOKEN') or old.consumed_at is not null or new.consumed_at is null) then
    raise exception 'AUTH_TOKEN_ALREADY_CONSUMED' using errcode='55000';
  end if;
  if new.status='ROTATING' and (old.status<>'ACTIVE' or new.rotation_grace_expires_at is null) then
    raise exception 'AUTH_TOKEN_ROTATION_CONFLICT' using errcode='55000';
  end if;
  if old.status='ROTATING' and new.status not in('ROTATING','REVOKED') then
    raise exception 'AUTH_TOKEN_ROTATION_CONFLICT' using errcode='55000';
  end if;
  if new.version<>old.version+1 then raise exception 'IDENTITY_VERSION_CONFLICT' using errcode='40001'; end if;
  return new;
end $$;
drop trigger if exists trg_phase1a5_token_secret_guard on token_access_tokens;
create trigger trg_phase1a5_token_secret_guard before update on token_access_tokens for each row execute function phase1a5_protect_token_secret();

create or replace function phase1a5_reject_evidence_mutation()
returns trigger language plpgsql as $$ begin raise exception 'TOKEN_USAGE_EVIDENCE_IMMUTABLE' using errcode='55000'; end $$;
drop trigger if exists trg_phase1a5_usage_immutable on token_usage_events;
create trigger trg_phase1a5_usage_immutable before update or delete on token_usage_events for each row execute function phase1a5_reject_evidence_mutation();

create or replace function phase1a5_service_account_security_change()
returns trigger language plpgsql as $$
declare actor varchar(128);
begin
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'phase1a5-system');
  perform phase1a3_increment_principal_security_epoch(new.tenant_id,new.service_account_id,actor);
  return new;
end $$;
drop trigger if exists trg_phase1a5_service_account_epoch on token_service_accounts;
create trigger trg_phase1a5_service_account_epoch after update of owner_user_id,owner_department_id,permission_scopes,allowed_audiences,allowed_api_prefixes,allowed_cidrs,status,risk_level on token_service_accounts for each row execute function phase1a5_service_account_security_change();

create or replace function phase1a5_initialize_tenant_token_policy()
returns trigger language plpgsql as $$
begin
  perform set_config('app.current_tenant_id',new.tenant_id,true);
  perform set_config('app.current_actor_id',coalesce(nullif(new.updated_by,''),'system'),true);
  insert into token_tenant_policies(tenant_id,updated_by) values(new.tenant_id,coalesce(nullif(new.updated_by,''),'system')) on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase1a5_tenant_token_policy on tenants;
create trigger trg_phase1a5_tenant_token_policy after insert on tenants for each row execute function phase1a5_initialize_tenant_token_policy();
