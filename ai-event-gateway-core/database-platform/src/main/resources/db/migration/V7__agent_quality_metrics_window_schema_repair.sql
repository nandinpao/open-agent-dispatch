-- Repair additive schema drift observed on local clean-baseline development databases.
-- Some local volumes may contain an older agent_quality_metrics_window table while
-- Flyway schema history is recreated. V1 uses CREATE TABLE IF NOT EXISTS, so rerunning
-- V1 cannot add columns to that existing table. Keep this as an additive, idempotent
-- migration instead of editing V1 and invalidating released checksums.

alter table if exists agent_quality_metrics_window
  add column if not exists calculated_at timestamptz;

alter table if exists agent_quality_metrics_window
  add column if not exists source varchar(64);

update agent_quality_metrics_window
   set calculated_at = coalesce(calculated_at, updated_at, created_at, now()),
       source = coalesce(nullif(source, ''), 'REPAIRED_LOCAL_SCHEMA')
 where calculated_at is null
    or source is null
    or source = '';
