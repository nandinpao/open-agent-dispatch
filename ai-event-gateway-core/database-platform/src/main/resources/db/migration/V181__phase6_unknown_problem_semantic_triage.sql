-- Phase 6 — Unknown Problem / Semantic Triage.
-- Semantic Triage resolves WHAT only. Triage proposals cannot authorize, rank or select providers and cannot select A2A/MCP/Netty.

create table if not exists semantic_triage_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  display_name varchar(255) not null,
  min_classification_confidence numeric(6,5) not null,
  min_capability_resolution_confidence numeric(6,5) not null,
  max_capability_suggestions int not null default 10,
  require_human_review_on_capability_gap boolean not null default true,
  status varchar(24) not null default 'DRAFT',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,policy_id),
  constraint semantic_triage_policy_classification_confidence_check check (min_classification_confidence between 0 and 1),
  constraint semantic_triage_policy_capability_confidence_check check (min_capability_resolution_confidence between 0 and 1),
  constraint semantic_triage_policy_max_suggestions_check check (max_capability_suggestions between 1 and 100),
  constraint semantic_triage_policy_status_check check (status in ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  constraint semantic_triage_policy_version_check check (version >= 1)
);
create unique index if not exists uq_semantic_triage_active_policy_per_tenant on semantic_triage_policies(tenant_id) where status='ACTIVE';
comment on table semantic_triage_policies is 'Phase 6 confidence/human-review policy. Thresholds are Tenant configuration, never provider routing weights.';

create table if not exists semantic_triage_policy_versions (
  tenant_id varchar(64) not null,
  policy_id varchar(160) not null,
  version int not null,
  snapshot_json jsonb not null,
  change_reason varchar(512) not null,
  actor_ref varchar(255) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,policy_id,version),
  constraint fk_semantic_triage_policy_version foreign key (tenant_id,policy_id) references semantic_triage_policies(tenant_id,policy_id) on delete cascade,
  constraint semantic_triage_policy_snapshot_object check (jsonb_typeof(snapshot_json)='object')
);

create table if not exists semantic_triage_requests (
  tenant_id varchar(64) not null,
  request_id varchar(160) not null,
  task_ref varchar(255),
  service_code varchar(160),
  problem_statement text,
  context_refs_json jsonb not null default '[]'::jsonb,
  input_context_json jsonb not null default '{}'::jsonb,
  data_classification varchar(80),
  status varchar(40) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,request_id),
  constraint semantic_triage_request_context_refs_array check (jsonb_typeof(context_refs_json)='array'),
  constraint semantic_triage_request_context_object check (jsonb_typeof(input_context_json)='object'),
  constraint semantic_triage_request_status_check check (status in ('KNOWN_FAST_PATH','TRIAGE_REQUIRED','REQUIREMENTS_PROPOSED','CLOSED'))
);
create index if not exists idx_semantic_triage_requests on semantic_triage_requests(tenant_id,status,created_at desc);
comment on table semantic_triage_requests is 'Phase 6 WHAT request. No target Domain/System/Pool/Agent/provider/protocol fields exist.';

create table if not exists semantic_triage_proposals (
  tenant_id varchar(64) not null,
  proposal_id varchar(160) not null,
  request_id varchar(160) not null,
  proposer_type varchar(40) not null,
  proposer_ref varchar(255),
  classification_json jsonb not null,
  classification_confidence numeric(6,5) not null,
  capability_resolution_confidence numeric(6,5) not null,
  capability_suggestions_json jsonb not null default '[]'::jsonb,
  investigation_suggestions_json jsonb not null default '[]'::jsonb,
  explanatory_taxonomies_json jsonb not null default '[]'::jsonb,
  rationale text,
  proposed_at timestamptz not null default now(),
  primary key (tenant_id,proposal_id),
  constraint fk_semantic_triage_proposal_request foreign key (tenant_id,request_id) references semantic_triage_requests(tenant_id,request_id),
  constraint semantic_triage_proposer_type_check check (proposer_type in ('TRIAGE_AGENT','HUMAN_ANALYST','SYSTEM_IMPORT')),
  constraint semantic_triage_classification_object check (jsonb_typeof(classification_json)='object'),
  constraint semantic_triage_suggestions_array check (jsonb_typeof(capability_suggestions_json)='array'),
  constraint semantic_triage_investigation_array check (jsonb_typeof(investigation_suggestions_json)='array'),
  constraint semantic_triage_taxonomy_array check (jsonb_typeof(explanatory_taxonomies_json)='array'),
  constraint semantic_triage_classification_confidence_check check (classification_confidence between 0 and 1),
  constraint semantic_triage_capability_confidence_check check (capability_resolution_confidence between 0 and 1)
);
create index if not exists idx_semantic_triage_proposals on semantic_triage_proposals(tenant_id,request_id,proposed_at desc);
comment on table semantic_triage_proposals is 'Append-only semantic proposals. Agent proposals are evidence, never executable authority.';

create table if not exists semantic_triage_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(160) not null,
  request_id varchar(160) not null,
  decision_mode varchar(16) not null default 'PREVIEW',
  result varchar(48) not null,
  service_code varchar(160),
  classification_json jsonb,
  classification_confidence numeric(6,5) not null default 0,
  capability_resolution_confidence numeric(6,5) not null default 0,
  triage_policy_id varchar(160),
  triage_policy_version int,
  accepted_requirements_json jsonb not null default '[]'::jsonb,
  reason_codes_json jsonb not null default '[]'::jsonb,
  requires_human_review boolean not null default false,
  decided_at timestamptz not null default now(),
  primary key (tenant_id,decision_id),
  constraint fk_semantic_triage_decision_request foreign key (tenant_id,request_id) references semantic_triage_requests(tenant_id,request_id),
  constraint semantic_triage_decision_mode_check check (decision_mode in ('PREVIEW','RUNTIME')),
  constraint semantic_triage_decision_result_check check (result in ('KNOWN_FAST_PATH','TRIAGE_REQUIRED','TRIAGE_POLICY_NOT_CONFIGURED','CAPABILITY_REQUIREMENTS_PROPOSED','HUMAN_REVIEW_REQUIRED','CAPABILITY_GAP')),
  constraint semantic_triage_decision_requirements_array check (jsonb_typeof(accepted_requirements_json)='array'),
  constraint semantic_triage_decision_reasons_array check (jsonb_typeof(reason_codes_json)='array'),
  constraint semantic_triage_decision_classification_confidence_check check (classification_confidence between 0 and 1),
  constraint semantic_triage_decision_capability_confidence_check check (capability_resolution_confidence between 0 and 1)
);
create index if not exists idx_semantic_triage_decisions on semantic_triage_decisions(tenant_id,request_id,decided_at desc);
comment on table semantic_triage_decisions is 'Append-only Phase 6 WHAT evidence. Accepted requirements must return to WHO CAN/WHO MAY/WHO SHOULD/HOW separately.';

alter table semantic_triage_policies enable row level security;
drop policy if exists tenant_isolation on semantic_triage_policies;
create policy tenant_isolation on semantic_triage_policies using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table semantic_triage_policy_versions enable row level security;
drop policy if exists tenant_isolation on semantic_triage_policy_versions;
create policy tenant_isolation on semantic_triage_policy_versions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table semantic_triage_requests enable row level security;
drop policy if exists tenant_isolation on semantic_triage_requests;
create policy tenant_isolation on semantic_triage_requests using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table semantic_triage_proposals enable row level security;
drop policy if exists tenant_isolation on semantic_triage_proposals;
create policy tenant_isolation on semantic_triage_proposals using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
alter table semantic_triage_decisions enable row level security;
drop policy if exists tenant_isolation on semantic_triage_decisions;
create policy tenant_isolation on semantic_triage_decisions using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_semantic_triage_policy_version_mutation() returns trigger language plpgsql as $$ begin raise exception 'SEMANTIC_TRIAGE_POLICY_VERSION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_semantic_triage_policy_version_immutable on semantic_triage_policy_versions;
create trigger trg_semantic_triage_policy_version_immutable before update or delete on semantic_triage_policy_versions for each row execute function prevent_semantic_triage_policy_version_mutation();
create or replace function prevent_semantic_triage_proposal_mutation() returns trigger language plpgsql as $$ begin raise exception 'SEMANTIC_TRIAGE_PROPOSAL_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_semantic_triage_proposal_immutable on semantic_triage_proposals;
create trigger trg_semantic_triage_proposal_immutable before update or delete on semantic_triage_proposals for each row execute function prevent_semantic_triage_proposal_mutation();
create or replace function prevent_semantic_triage_decision_mutation() returns trigger language plpgsql as $$ begin raise exception 'SEMANTIC_TRIAGE_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_semantic_triage_decision_immutable on semantic_triage_decisions;
create trigger trg_semantic_triage_decision_immutable before update or delete on semantic_triage_decisions for each row execute function prevent_semantic_triage_decision_mutation();

insert into capability_architecture_evidence(evidence_id,architecture_version,principle,effective_at,created_by)
values('phase6-semantic-triage-authority','PHASE6_UNKNOWN_PROBLEM_SEMANTIC_TRIAGE','KNOWN_WORK_BYPASSES_AI_UNKNOWN_WORK_MAY_RECEIVE_SEMANTIC_WHAT_PROPOSALS_BUT_AGENT_NEVER_AUTHORIZES_SELECTS_PROVIDER_OR_PROTOCOL',now(),'V181')
on conflict(evidence_id) do nothing;
