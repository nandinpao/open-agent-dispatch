-- OpenDispatch v17 P2.3A: Identity activation delivery tracking.
-- One-time secrets remain transient and are NEVER stored here. This table records only
-- non-secret delivery evidence so administrators can distinguish token issuance from delivery.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v17-p23a-activation-delivery',true);

create table if not exists iam_identity_activation_deliveries (
  tenant_id varchar(64) not null,
  delivery_id varchar(128) not null,
  user_id varchar(128) not null,
  token_id varchar(128) not null,
  purpose varchar(48) not null,
  delivery_method varchar(32) not null,
  delivery_status varchar(32) not null,
  recipient_reference varchar(320) not null default '',
  issued_at timestamptz not null,
  delivered_at timestamptz,
  failed_at timestamptz,
  expires_at timestamptz not null,
  failure_code varchar(96) not null default '',
  correlation_id varchar(128) not null default '',
  created_by varchar(128) not null,
  version bigint not null default 1,
  primary key(tenant_id,delivery_id),
  foreign key(tenant_id) references tenants(tenant_id),
  foreign key(tenant_id,token_id) references token_access_tokens(tenant_id,token_id),
  check (delivery_method in ('EMAIL','MANUAL','DEVELOPMENT_FILE')),
  check (delivery_status in ('ISSUED','QUEUED','DELIVERED','FAILED','CONSUMED','EXPIRED','REVOKED'))
);

create index if not exists idx_iam_activation_delivery_user
  on iam_identity_activation_deliveries(tenant_id,user_id,issued_at desc);
create index if not exists idx_iam_activation_delivery_status
  on iam_identity_activation_deliveries(tenant_id,delivery_status,issued_at desc);
create unique index if not exists uq_iam_activation_delivery_token_method
  on iam_identity_activation_deliveries(tenant_id,token_id,delivery_method);

alter table iam_identity_activation_deliveries enable row level security;
alter table iam_identity_activation_deliveries force row level security;
drop policy if exists tenant_isolation on iam_identity_activation_deliveries;
create policy tenant_isolation on iam_identity_activation_deliveries
  using (iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id())
  with check (iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id());

comment on table iam_identity_activation_deliveries is
'Non-secret delivery receipts for Person activation/password-setup credentials. One-time token plaintext and setup URLs must never be persisted.';
comment on column iam_identity_activation_deliveries.delivery_status is
'Delivery lifecycle is independent from token lifecycle: ISSUED/QUEUED/DELIVERED/FAILED/CONSUMED/EXPIRED/REVOKED.';

-- Keep delivery lifecycle evidence aligned with terminal one-time token states without
-- persisting token plaintext. Expiry is also normalized at query time because wall-clock
-- expiry may happen without a token update event.
create or replace function v17_p23a_sync_activation_delivery_token_status()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if new.token_type not in ('INVITATION_TOKEN','PASSWORD_RESET_TOKEN') then
    return new;
  end if;
  if new.status='CONSUMED' then
    update iam_identity_activation_deliveries
       set delivery_status='CONSUMED',version=version+1
     where tenant_id=new.tenant_id and token_id=new.token_id
       and delivery_status not in ('CONSUMED','REVOKED');
  elsif new.status='REVOKED' then
    update iam_identity_activation_deliveries
       set delivery_status='REVOKED',version=version+1
     where tenant_id=new.tenant_id and token_id=new.token_id
       and delivery_status<>'REVOKED';
  end if;
  return new;
end $$;

drop trigger if exists trg_v17_p23a_activation_delivery_token_status on token_access_tokens;
create trigger trg_v17_p23a_activation_delivery_token_status
  after update of status on token_access_tokens
  for each row execute function v17_p23a_sync_activation_delivery_token_status();

revoke all on function v17_p23a_sync_activation_delivery_token_status() from public;
