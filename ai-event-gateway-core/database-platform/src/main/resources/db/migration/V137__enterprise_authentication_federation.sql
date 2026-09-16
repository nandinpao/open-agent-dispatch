-- OpenDispatch v17 P2.4: Enterprise Authentication Federation.
-- External providers authenticate a canonical Human User. Tenant/Department/Group/Role Binding
-- remain the only authorization authority. Root/break-glass remains local-only.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v17-p24-enterprise-authentication-federation',true);

create table if not exists iam_authentication_providers (
  tenant_id varchar(64) not null,
  provider_id varchar(128) not null,
  provider_code varchar(80) not null,
  provider_type varchar(24) not null,
  display_name varchar(160) not null,
  status varchar(24) not null default 'ACTIVE',
  issuer_uri varchar(1024) not null,
  client_id varchar(320) not null,
  client_secret_ref varchar(320) not null,
  scopes varchar(128)[] not null default array['openid','profile','email']::varchar[],
  subject_claim varchar(96) not null default 'sub',
  username_claim varchar(96) not null default 'preferred_username',
  email_claim varchar(96) not null default 'email',
  display_name_claim varchar(96) not null default 'name',
  amr_claim varchar(96) not null default 'amr',
  acr_claim varchar(96) not null default 'acr',
  upstream_mfa_mode varchar(40) not null default 'REQUIRE_ASSERTED',
  trusted_amr_values varchar(96)[] not null default array['mfa','otp','hwk','swk']::varchar[],
  trusted_acr_values varchar(256)[] not null default array[]::varchar[],
  link_mode varchar(48) not null default 'EXPLICIT_ONLY',
  jit_mode varchar(48) not null default 'DISABLED',
  authorization_enabled boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  version bigint not null default 1,
  primary key(tenant_id,provider_id),
  foreign key(tenant_id) references tenants(tenant_id),
  unique(tenant_id,provider_code),
  check(provider_type in('OIDC','SAML')),
  check(status in('ACTIVE','DISABLED')),
  check(upstream_mfa_mode in('NONE','TRUST_AMR','TRUST_ACR','TRUST_AMR_OR_ACR','REQUIRE_ASSERTED')),
  check(link_mode in('EXPLICIT_ONLY','VERIFIED_EMAIL_EXISTING_USER')),
  check(jit_mode in('DISABLED','CREATE_ACTIVE_SSO_USER')),
  check(client_secret_ref like 'env:%' or client_secret_ref like 'property:%'),
  constraint ck_iam_auth_provider_no_external_authorization check(authorization_enabled=false),
  check(cardinality(scopes)>0),
  check('openid'=any(scopes)),
  check(version>0)
);
create unique index if not exists uq_iam_auth_provider_tenant_issuer_client
  on iam_authentication_providers(tenant_id,lower(issuer_uri),client_id);
create unique index if not exists uq_iam_auth_provider_tenant_code_ci
  on iam_authentication_providers(tenant_id,lower(provider_code));
create index if not exists idx_iam_auth_provider_status
  on iam_authentication_providers(tenant_id,status,provider_code);

create table if not exists iam_authentication_federation_policies (
  tenant_id varchar(64) primary key references tenants(tenant_id),
  local_login_enabled boolean not null default true,
  oidc_login_enabled boolean not null default false,
  saml_login_enabled boolean not null default false,
  provider_discovery_enabled boolean not null default true,
  default_provider_id varchar(128),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  version bigint not null default 1,
  foreign key(tenant_id,default_provider_id) references iam_authentication_providers(tenant_id,provider_id),
  check(version>0)
);
insert into iam_authentication_federation_policies(tenant_id,updated_by)
select tenant_id,'v17-p24-enterprise-authentication-federation' from tenants
on conflict(tenant_id) do nothing;

create or replace function v17_p24_seed_federation_policy()
returns trigger language plpgsql as $$
begin
  insert into iam_authentication_federation_policies(tenant_id,updated_by)
  values(new.tenant_id,'tenant-create:federation-default')
  on conflict(tenant_id) do nothing;
  return new;
end $$;
drop trigger if exists trg_v17_p24_seed_federation_policy on tenants;
create trigger trg_v17_p24_seed_federation_policy
after insert on tenants for each row execute function v17_p24_seed_federation_policy();

create table if not exists iam_oidc_login_attempts (
  tenant_id varchar(64) not null,
  attempt_id varchar(128) not null,
  provider_id varchar(128) not null,
  state_hash varchar(128) not null,
  nonce_hash varchar(128) not null,
  redirect_path varchar(512) not null default '/dashboard',
  status varchar(24) not null default 'PENDING',
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  failure_code varchar(128) not null default '',
  correlation_id varchar(128) not null default '',
  version bigint not null default 1,
  primary key(tenant_id,attempt_id),
  foreign key(tenant_id,provider_id) references iam_authentication_providers(tenant_id,provider_id),
  unique(state_hash),
  check(status in('PENDING','CONSUMED','FAILED','EXPIRED')),
  check(expires_at>issued_at),
  check(version>0)
);
create index if not exists idx_iam_oidc_login_attempt_expiry
  on iam_oidc_login_attempts(tenant_id,status,expires_at);

alter table iam_identity_credential_links add column if not exists provider_id varchar(128);
alter table iam_identity_credential_links add column if not exists provider_tenant_id varchar(64);
alter table iam_identity_credential_links add column if not exists issuer_uri varchar(1024);
alter table iam_identity_credential_links add column if not exists upstream_username varchar(320);
alter table iam_identity_credential_links add column if not exists upstream_email varchar(320);
alter table iam_identity_credential_links add column if not exists upstream_email_verified boolean;

do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_iam_credential_link_provider') then
    alter table iam_identity_credential_links add constraint fk_iam_credential_link_provider
      foreign key(provider_tenant_id,provider_id) references iam_authentication_providers(tenant_id,provider_id) not valid;
  end if;
end $$;
alter table iam_identity_credential_links validate constraint fk_iam_credential_link_provider;

-- V110's provider_type + subject key is insufficient for OIDC because `sub` is only unique within an issuer/provider.
alter table iam_identity_credential_links drop constraint if exists uq_iam_identity_credential_provider_subject;
create unique index if not exists uq_iam_identity_credential_local_subject
  on iam_identity_credential_links(provider_type,normalized_provider_subject)
  where provider_id is null;
create unique index if not exists uq_iam_identity_credential_external_subject
  on iam_identity_credential_links(provider_tenant_id,provider_id,normalized_provider_subject)
  where provider_id is not null;
create unique index if not exists uq_iam_identity_credential_external_user_provider
  on iam_identity_credential_links(provider_tenant_id,provider_id,subject_id)
  where provider_id is not null and status='ACTIVE';

create or replace function v17_p24_validate_external_credential_link()
returns trigger language plpgsql as $$
begin
  if new.provider_type in('OIDC','SAML') then
    if new.subject_type<>'HUMAN_USER' then
      raise exception 'AUTH_FEDERATION_ROOT_LINK_FORBIDDEN' using errcode='23514';
    end if;
    if new.provider_id is null or new.provider_tenant_id is null or new.issuer_uri is null then
      raise exception 'AUTH_FEDERATION_PROVIDER_REFERENCE_REQUIRED' using errcode='23514';
    end if;
    if not exists(
      select 1 from org_tenant_memberships m
      where m.tenant_id=new.provider_tenant_id and m.user_id=new.subject_id
        and m.status in('INVITED','ACTIVE','SUSPENDED')
        and (m.expires_at is null or m.expires_at>now())
    ) then
      raise exception 'AUTH_FEDERATION_TENANT_MEMBERSHIP_REQUIRED' using errcode='23514';
    end if;
  elsif new.provider_id is not null or new.provider_tenant_id is not null then
    raise exception 'AUTH_FEDERATION_PROVIDER_REFERENCE_INVALID' using errcode='23514';
  end if;
  return new;
end $$;
drop trigger if exists trg_v17_p24_validate_external_credential_link on iam_identity_credential_links;
create trigger trg_v17_p24_validate_external_credential_link
before insert or update on iam_identity_credential_links
for each row execute function v17_p24_validate_external_credential_link();

-- Tenant isolation. Provider discovery uses an explicit Tenant context installed from the requested workspace.
alter table iam_authentication_providers enable row level security;
alter table iam_authentication_providers force row level security;
drop policy if exists tenant_isolation on iam_authentication_providers;
create policy tenant_isolation on iam_authentication_providers using(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id()) with check(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id());
alter table iam_authentication_federation_policies enable row level security;
alter table iam_authentication_federation_policies force row level security;
drop policy if exists tenant_isolation on iam_authentication_federation_policies;
create policy tenant_isolation on iam_authentication_federation_policies using(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id()) with check(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id());
alter table iam_oidc_login_attempts enable row level security;
alter table iam_oidc_login_attempts force row level security;
drop policy if exists tenant_isolation on iam_oidc_login_attempts;
create policy tenant_isolation on iam_oidc_login_attempts using(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id()) with check(iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id());

comment on table iam_authentication_providers is 'Tenant-scoped external authentication provider configuration. Client secrets are references only; plaintext secrets are never stored.';
comment on table iam_authentication_federation_policies is 'Tenant authentication policy. Root/break-glass local authentication is instance-owned and cannot be disabled here.';
comment on table iam_oidc_login_attempts is 'Replay-resistant OIDC login state evidence; no authorization-code, access-token, refresh-token, PKCE verifier, or ID-token plaintext is persisted.';
comment on column iam_identity_credential_links.provider_id is 'P2.4 provider-specific canonical identity link. External OIDC/SAML subject values are never an authorization source.';

