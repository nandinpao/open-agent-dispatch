-- C0-B2 — External A2A F0 TrustAssurancePolicy.
-- Evidence facts are policy inputs. Only computed grants may be consumed by external-A2A runtime authorization.

create table if not exists a2a_trust_assurance_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(180) not null,
  display_name varchar(255) not null,
  description text,
  policy_status varchar(24) not null default 'DRAFT',
  policy_version bigint not null default 1,
  created_by varchar(160) not null,
  created_at timestamptz not null default now(),
  activated_at timestamptz,
  retired_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (tenant_id,policy_id),
  constraint ck_a2a_trust_assurance_policy_status check (policy_status in ('DRAFT','ACTIVE','RETIRED')),
  constraint ck_a2a_trust_assurance_policy_version check (policy_version>0),
  constraint ck_a2a_trust_assurance_policy_lifecycle check (
    (policy_status='DRAFT' and activated_at is null and retired_at is null)
    or (policy_status='ACTIVE' and activated_at is not null and retired_at is null)
    or (policy_status='RETIRED' and activated_at is not null and retired_at is not null)
  )
);

create unique index if not exists uq_a2a_trust_assurance_active_policy
  on a2a_trust_assurance_policies(tenant_id)
  where policy_status='ACTIVE';

create table if not exists a2a_trust_assurance_rules (
  tenant_id varchar(64) not null,
  policy_id varchar(180) not null,
  rule_id varchar(180) not null,
  grant_type varchar(64) not null,
  subject_type varchar(24) not null,
  required_evidence_sets_json jsonb not null,
  description text,
  created_at timestamptz not null default now(),
  primary key (tenant_id,policy_id,rule_id),
  constraint fk_a2a_trust_assurance_rule_policy foreign key (tenant_id,policy_id)
    references a2a_trust_assurance_policies(tenant_id,policy_id) on delete cascade,
  constraint ck_a2a_trust_assurance_rule_grant check (grant_type in (
    'READ_ALLOWED','SENSITIVE_READ_ALLOWED','WRITE_ALLOWED','CRITICAL_ALLOWED'
  )),
  constraint ck_a2a_trust_assurance_rule_subject check (subject_type in ('PEER','INTERFACE')),
  constraint ck_a2a_trust_assurance_rule_sets check (
    jsonb_typeof(required_evidence_sets_json)='array'
    and jsonb_array_length(required_evidence_sets_json)>0
  )
);

create index if not exists idx_a2a_trust_assurance_rules_evaluate
  on a2a_trust_assurance_rules(tenant_id,policy_id,subject_type,grant_type);

-- Canonical evaluator shared by server-side WHO-CAN and Java policy explanation.
-- Outer array = ANY-OF alternative evidence sets; each inner array = ALL-OF evidence types.
create or replace function a2a_has_assurance_grant(
  p_tenant varchar,
  p_peer varchar,
  p_interface varchar,
  p_subject varchar,
  p_grant varchar
) returns boolean language sql stable as $$
  select exists (
    select 1
      from a2a_trust_assurance_policies p
      join a2a_trust_assurance_rules r
        on r.tenant_id=p.tenant_id and r.policy_id=p.policy_id
     where p.tenant_id=p_tenant
       and p_tenant=iam_current_tenant_id()
       and p.policy_status='ACTIVE'
       and r.subject_type=p_subject
       and r.grant_type=p_grant
       and exists (
         select 1
           from jsonb_array_elements(r.required_evidence_sets_json) evidence_set(value)
          where jsonb_typeof(evidence_set.value)='array'
            and jsonb_array_length(evidence_set.value)>0
            and not exists (
              select 1
                from jsonb_array_elements_text(evidence_set.value) required(evidence_type)
               where not exists (
                 select 1
                   from a2a_peer_trust_evidence e
                  where e.tenant_id=p_tenant
                    and e.peer_id=p_peer
                    and e.subject_type=p_subject
                    and ((p_subject='PEER' and e.interface_id is null)
                         or (p_subject='INTERFACE' and e.interface_id=p_interface))
                    and e.evidence_type=required.evidence_type
                    and e.evidence_status='ACTIVE'
                    and e.valid_from<=now()
                    and (e.valid_until is null or e.valid_until>now())
               )
            )
       )
  )
$$;

alter table a2a_trust_assurance_policies enable row level security;
drop policy if exists tenant_isolation on a2a_trust_assurance_policies;
create policy tenant_isolation on a2a_trust_assurance_policies
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

alter table a2a_trust_assurance_rules enable row level security;
drop policy if exists tenant_isolation on a2a_trust_assurance_rules;
create policy tenant_isolation on a2a_trust_assurance_rules
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

-- A policy is versioned governance. Rules may only be edited while their parent policy is DRAFT.
create or replace function protect_a2a_trust_assurance_rule_c0b2() returns trigger language plpgsql as $$
declare s text; t text; p text;
begin
  if tg_op='DELETE' then t:=old.tenant_id; p:=old.policy_id; else t:=new.tenant_id; p:=new.policy_id; end if;
  select policy_status into s from a2a_trust_assurance_policies where tenant_id=t and policy_id=p;
  if s is distinct from 'DRAFT' then
    raise exception 'C0_B2_ACTIVE_OR_RETIRED_POLICY_RULE_IMMUTABLE' using errcode='55000';
  end if;
  if tg_op='DELETE' then return old; else return new; end if;
end $$;

drop trigger if exists trg_a2a_trust_assurance_rule_c0b2 on a2a_trust_assurance_rules;
create trigger trg_a2a_trust_assurance_rule_c0b2
before insert or update or delete on a2a_trust_assurance_rules
for each row execute function protect_a2a_trust_assurance_rule_c0b2();

create or replace function protect_a2a_trust_assurance_policy_c0b2() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id
     or old.policy_id is distinct from new.policy_id
     or old.created_by is distinct from new.created_by
     or old.created_at is distinct from new.created_at
     or old.policy_version is distinct from new.policy_version then
    raise exception 'C0_B2_TRUST_ASSURANCE_POLICY_IDENTITY_IMMUTABLE' using errcode='55000';
  end if;
  if old.policy_status='ACTIVE' and new.policy_status not in ('ACTIVE','RETIRED') then
    raise exception 'C0_B2_ACTIVE_POLICY_CANNOT_RETURN_TO_DRAFT' using errcode='55000';
  end if;
  if old.policy_status='RETIRED' and new.policy_status<>'RETIRED' then
    raise exception 'C0_B2_RETIRED_POLICY_IMMUTABLE' using errcode='55000';
  end if;
  if old.policy_status<>'DRAFT' and (
       old.display_name is distinct from new.display_name
       or old.description is distinct from new.description) then
    raise exception 'C0_B2_NON_DRAFT_POLICY_CONTENT_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;

drop trigger if exists trg_a2a_trust_assurance_policy_c0b2 on a2a_trust_assurance_policies;
create trigger trg_a2a_trust_assurance_policy_c0b2
before update on a2a_trust_assurance_policies
for each row execute function protect_a2a_trust_assurance_policy_c0b2();

comment on table a2a_trust_assurance_policies is
'C0-B2 versioned TrustAssurancePolicy authority. Policies compute grants from current PeerTrustEvidence; administrators do not directly assign grants.';
comment on column a2a_trust_assurance_rules.required_evidence_sets_json is
'ANY-OF evidence sets. Each inner set is ALL-OF PeerTrustEvidence types. A grant is computed only when at least one complete set is currently effective.';
comment on column a2a_peer_registrations.trust_status is
'Legacy compatibility projection only. C0-B2 external-A2A runtime authorization MUST use computed TrustAssurancePolicy grants.';
comment on column a2a_peer_interfaces.trust_status is
'Legacy compatibility projection only. C0-B2 external-A2A runtime authorization MUST use computed TrustAssurancePolicy grants.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b2-trust-assurance-policy-v1','C0_B2_EXTERNAL_A2A_F0_TRUST_ASSURANCE',
       'EVIDENCE_TO_COMPUTED_GRANTS; REQUIRED_EVIDENCE_SETS_ANY_OF_ALL_OF; NO_DIRECT_GRANT_WRITE; LEGACY_TRUST_STATUS_NOT_RUNTIME_AUTHORITY',
       now(),'V213')
on conflict(contract_id) do nothing;
