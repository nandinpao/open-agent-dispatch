-- P4RA-H: governance UI read model, Access Review lifecycle, and Orphan Repair evidence.
-- Review decisions never silently mutate policy authority. Policy changes continue through P4RA-C services.

create table if not exists resource_access_review_campaigns (
  tenant_id varchar(64) not null,
  campaign_id varchar(128) not null,
  campaign_name varchar(200) not null,
  description text not null default '',
  resource_type_filter varchar(64),
  principal_type_filter varchar(32),
  campaign_status varchar(32) not null,
  created_by varchar(128) not null,
  activated_by varchar(128),
  completed_by varchar(128),
  due_at timestamptz not null,
  total_items bigint not null default 0,
  open_items bigint not null default 0,
  idempotency_key varchar(256) not null,
  version bigint not null default 1,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  primary key (tenant_id,campaign_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id) references tenants(tenant_id),
  check (campaign_status in ('DRAFT','ACTIVE','COMPLETED','CANCELLED')),
  check (principal_type_filter is null or principal_type_filter in ('USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT')),
  check (total_items >= 0 and open_items >= 0 and open_items <= total_items),
  check (version > 0),
  check (campaign_status <> 'ACTIVE' or nullif(activated_by,'') is not null),
  check (campaign_status <> 'COMPLETED' or (nullif(completed_by,'') is not null and open_items=0))
);

create table if not exists resource_access_review_items (
  tenant_id varchar(64) not null,
  item_id varchar(128) not null,
  campaign_id varchar(128) not null,
  source_type varchar(32) not null,
  source_id varchar(128) not null,
  principal_type varchar(32) not null default '',
  principal_id varchar(128) not null default '',
  permission_code varchar(160) not null default '',
  resource_type varchar(64) not null default '',
  scope_type varchar(32) not null default '',
  scope_ref_id varchar(128) not null default '',
  risk_level varchar(32) not null,
  review_status varchar(32) not null,
  reviewer_id varchar(128),
  decision_reason text not null default '',
  action_required boolean not null default false,
  version bigint not null default 1,
  due_at timestamptz not null,
  reviewed_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  primary key (tenant_id,item_id),
  unique (tenant_id,campaign_id,source_type,source_id),
  foreign key (tenant_id,campaign_id) references resource_access_review_campaigns(tenant_id,campaign_id) on delete cascade,
  check (source_type in ('SCOPE_GRANT','EXPLICIT_DENY','ORPHAN_RESOURCE','SERVICE_ACCOUNT_SCOPE','BREAK_GLASS')),
  check (risk_level in ('LOW','MODERATE','HIGH','CRITICAL')),
  check (review_status in ('OPEN','CONFIRMED','REDUCED','REVOKED','OWNER_MISSING','EXPIRED','ESCALATED')),
  check (version > 0),
  check ((review_status='OPEN' and reviewer_id is null and reviewed_at is null) or
         (review_status<>'OPEN' and nullif(reviewer_id,'') is not null and reviewed_at is not null)),
  check (review_status not in ('REDUCED','REVOKED','OWNER_MISSING','EXPIRED','ESCALATED') or action_required)
);

create table if not exists resource_access_review_events (
  tenant_id varchar(64) not null,
  event_id varchar(128) not null,
  campaign_id varchar(128) not null,
  item_id varchar(128) not null default '',
  previous_status varchar(32),
  resulting_status varchar(32) not null,
  actor_id varchar(128) not null,
  reason text not null,
  action_required boolean not null default false,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  resulting_version bigint not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,campaign_id) references resource_access_review_campaigns(tenant_id,campaign_id),
  check (resulting_version > 0)
);

create table if not exists resource_orphan_repair_events (
  tenant_id varchar(64) not null,
  event_id varchar(128) not null,
  repair_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  event_type varchar(32) not null,
  actor_id varchar(128) not null,
  reason text not null,
  proposed_owner_department_id varchar(128),
  proposed_owner_group_id varchar(128),
  proposed_steward_user_id varchar(128),
  expected_resource_version bigint not null,
  impact_snapshot jsonb not null default '{}'::jsonb,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  unique (tenant_id,idempotency_key),
  foreign key (tenant_id,repair_id) references resource_orphan_repairs(tenant_id,repair_id),
  check (event_type in ('IMPACT_PREVIEWED','OWNER_ASSIGNED','WAIVED','ESCALATED')),
  check (expected_resource_version > 0)
);

create index if not exists idx_resource_review_campaign_queue
  on resource_access_review_campaigns(tenant_id,campaign_status,due_at,updated_at desc,campaign_id desc);
create index if not exists idx_resource_review_item_queue
  on resource_access_review_items(tenant_id,campaign_id,review_status,due_at,updated_at desc,item_id desc);
create index if not exists idx_resource_review_item_subject
  on resource_access_review_items(tenant_id,principal_type,principal_id,resource_type,source_type);
create index if not exists idx_resource_review_events_campaign
  on resource_access_review_events(tenant_id,campaign_id,occurred_at desc);
create index if not exists idx_resource_orphan_repair_events_resource
  on resource_orphan_repair_events(tenant_id,resource_type,resource_id,occurred_at desc);

-- Access Review and Orphan Repair evidence are immutable.
drop trigger if exists trg_resource_access_review_event_immutable on resource_access_review_events;
create trigger trg_resource_access_review_event_immutable before update or delete on resource_access_review_events
for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_orphan_repair_event_immutable on resource_orphan_repair_events;
create trigger trg_resource_orphan_repair_event_immutable before update or delete on resource_orphan_repair_events
for each row execute function p4ra_reject_append_only_mutation();

do $$
declare table_name text;
begin
  foreach table_name in array array[
    'resource_access_review_campaigns','resource_access_review_items','resource_access_review_events','resource_orphan_repair_events'
  ] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists tenant_isolation on %I',table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('resource.governance.read','RESOURCE_GOVERNANCE','READ','Read the Resource Access governance dashboard and resource overview.','HIGH',array['TENANT'],true),
 ('resource.review.read','RESOURCE_ACCESS_REVIEW','READ','Read Access Review campaigns and items.','HIGH',array['TENANT'],true),
 ('resource.review.manage','RESOURCE_ACCESS_REVIEW','MANAGE','Create, activate, complete, cancel, and decide Access Review items.','CRITICAL',array['TENANT'],true),
 ('resource.orphan.read','RESOURCE_ORPHAN','READ','Read orphan resource repair queue and impact previews.','HIGH',array['TENANT'],true),
 ('resource.orphan.repair','RESOURCE_ORPHAN','MANAGE','Assign canonical ownership to an orphan resource after impact preview.','CRITICAL',array['TENANT'],true)
on conflict(permission_point) do update set
 resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=true,active=true,
 version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('ACCESS_REVIEW_CAMPAIGN_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The Access Review campaign was not found.'),
 ('ACCESS_REVIEW_CAMPAIGN_NOT_DRAFT',409,'RESOURCE_ACCESS',false,'Only a draft Access Review campaign can be activated.'),
 ('ACCESS_REVIEW_CAMPAIGN_NOT_ACTIVE',409,'RESOURCE_ACCESS',false,'The Access Review campaign is not active.'),
 ('ACCESS_REVIEW_CAMPAIGN_HAS_OPEN_ITEMS',409,'RESOURCE_ACCESS',false,'All Access Review items must be decided before completion.'),
 ('ACCESS_REVIEW_ITEM_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The Access Review item was not found.'),
 ('ACCESS_REVIEW_ITEM_ALREADY_DECIDED',409,'RESOURCE_ACCESS',false,'The Access Review item already has a terminal decision.'),
 ('ACCESS_REVIEW_SELF_REVIEW_PROHIBITED',409,'RESOURCE_ACCESS',false,'A user cannot review their own access.'),
 ('RESOURCE_ORPHAN_REPAIR_NOT_OPEN',409,'RESOURCE_ACCESS',false,'The orphan repair case is not open.'),
 ('RESOURCE_GOVERNANCE_OVERVIEW_NOT_FOUND',404,'RESOURCE_ACCESS',false,'The Resource governance overview is unavailable.')
on conflict(reason_code) do update set
 http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,
 message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

comment on table resource_access_review_campaigns is 'P4RA-H Access Review campaign current state with optimistic versioning.';
comment on table resource_access_review_items is 'P4RA-H immutable-snapshot review subjects; terminal results request follow-up policy actions rather than silently mutating policy.';
comment on table resource_access_review_events is 'Append-only Access Review campaign and item lifecycle evidence.';
comment on table resource_orphan_repair_events is 'Append-only impact-preview and canonical ownership repair evidence.';
