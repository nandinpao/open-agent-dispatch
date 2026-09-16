alter table iam_legacy_identity_migrations add constraint ck_iam_legacy_migration_status
  check (migration_status in ('PLANNED','MIGRATING','MIGRATED','FAILED','ROLLED_BACK'));
alter table iam_legacy_identity_migrations add constraint ck_iam_legacy_no_password_material
  check (password_material_migrated = false and reset_required = true);
alter table iam_auth_cutover_events add constraint ck_iam_auth_cutover_mode
  check (from_mode in ('LEGACY_ONLY','DUAL','IAM_ONLY') and to_mode in ('LEGACY_ONLY','DUAL','IAM_ONLY'));
alter table iam_auth_cutover_events add constraint ck_iam_auth_cutover_outcome
  check (outcome in ('PLANNED','EXECUTED','REJECTED','ROLLED_BACK'));
create or replace function iam_prevent_cutover_evidence_mutation() returns trigger language plpgsql as $$ begin raise exception 'IAM_CUTOVER_EVIDENCE_IMMUTABLE'; end $$;
create trigger trg_iam_cutover_events_no_update before update or delete on iam_auth_cutover_events for each row execute function iam_prevent_cutover_evidence_mutation();
revoke insert,update,delete on iam_auth_cutover_events from opendispatch_runtime;
revoke insert,update,delete on iam_legacy_identity_migrations from opendispatch_runtime;
