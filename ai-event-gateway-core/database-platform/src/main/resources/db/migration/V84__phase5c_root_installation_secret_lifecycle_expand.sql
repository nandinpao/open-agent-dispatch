-- Phase 5C expand: installation-secret Root lifecycle and immutable bootstrap evidence.

alter table auth_root_bootstrap_state
  add column if not exists installation_completed_at timestamptz,
  add column if not exists initial_password_changed_at timestamptz,
  add column if not exists installation_credential_version bigint,
  add column if not exists installation_correlation_id varchar(128);

create table if not exists auth_root_installation_events (
  event_id uuid primary key,
  event_type varchar(64) not null,
  root_identity_id varchar(128) not null,
  bootstrap_source varchar(32) not null,
  credential_version bigint,
  correlation_id varchar(128),
  details jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  foreign key(root_identity_id) references iam_root_identities(root_identity_id)
);
create index if not exists idx_auth_root_installation_events_time
  on auth_root_installation_events(occurred_at desc,event_id);

-- No historical INSTALLATION_SECRET runtime existed before this migration. Existing interactive rows remain unchanged.
