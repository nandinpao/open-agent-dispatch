-- C0-A4: Result submission attempts are append-only evidence; canonical idempotency
-- ownership belongs only to an accepted canonical Result.

-- V40 made the first submitted attempt the durable idempotency owner. That causes
-- transient verification failures to burn the key forever. Remove that authority.
alter table a2a_result_attempts
  drop constraint if exists a2a_result_attempts_tenant_id_idempotency_key_key;

create index if not exists idx_a2a_result_attempts_idempotency_evidence
  on a2a_result_attempts(tenant_id,idempotency_key,received_at desc);

create table if not exists a2a_result_idempotency_claims (
  tenant_id varchar(64) not null,
  idempotency_key varchar(255) not null,
  a2a_request_id varchar(128) not null,
  a2a_result_id varchar(128) not null,
  acceptance_attempt_id varchar(128),
  result_fingerprint varchar(128) not null,
  claimed_at timestamptz not null default now(),
  primary key (tenant_id,idempotency_key),
  unique (tenant_id,a2a_result_id)
);

-- Existing canonical Results become the authority during migration. Legacy rows may
-- predate fingerprint/acceptance-attempt columns; preserve a deterministic migration
-- identity without turning a historical submission attempt into authority.
insert into a2a_result_idempotency_claims(
  tenant_id,idempotency_key,a2a_request_id,a2a_result_id,acceptance_attempt_id,result_fingerprint,claimed_at)
select tenant_id,idempotency_key,a2a_request_id,a2a_result_id,acceptance_attempt_id,
       coalesce(result_fingerprint,payload_hash,'legacy-result:'||a2a_result_id),
       coalesce(accepted_at,created_at,now())
from a2a_results
where idempotency_key is not null
on conflict(tenant_id,idempotency_key) do nothing;

do $$ begin
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_idem_claim_request') then
    alter table a2a_result_idempotency_claims add constraint fk_a2a_result_idem_claim_request
      foreign key (tenant_id,a2a_request_id) references a2a_requests(tenant_id,a2a_request_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_idem_claim_result') then
    alter table a2a_result_idempotency_claims add constraint fk_a2a_result_idem_claim_result
      foreign key (tenant_id,a2a_result_id) references a2a_results(tenant_id,a2a_result_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_idem_claim_attempt') then
    alter table a2a_result_idempotency_claims add constraint fk_a2a_result_idem_claim_attempt
      foreign key (tenant_id,acceptance_attempt_id) references a2a_result_attempts(tenant_id,attempt_id) not valid;
  end if;
end $$;

-- Canonical idempotency authority is immutable. New attempts never mutate or replace it.
drop trigger if exists trg_a2a_result_idempotency_claim_immutable on a2a_result_idempotency_claims;
create trigger trg_a2a_result_idempotency_claim_immutable
before update or delete on a2a_result_idempotency_claims
for each row execute function reject_a2a_result_evidence_mutation();

comment on table a2a_result_idempotency_claims is
  'C0-A4 canonical A2A Result idempotency authority. Created only with an accepted canonical Result; submission attempts remain append-only evidence.';
comment on column a2a_result_attempts.idempotency_key is
  'Submission evidence correlation only. C0-A4 removes uniqueness; this column is never canonical idempotency authority.';
