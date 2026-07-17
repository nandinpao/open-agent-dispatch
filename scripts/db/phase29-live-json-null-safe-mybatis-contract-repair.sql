-- Phase 29 live DB guard for JSON null-safe MyBatis contract.
-- The primary repair is in MyBatis XML. This SQL is intentionally limited to
-- hardening existing rows/defaults for the event intake path so live dev DBs do
-- not retain nullable JSON state from earlier iterations.

alter table if exists routing_decisions
  alter column user_facing_error_json set default '{}'::jsonb,
  alter column candidates_json set default '[]'::jsonb;

update routing_decisions
set user_facing_error_json = '{}'::jsonb
where user_facing_error_json is null;

update routing_decisions
set candidates_json = '[]'::jsonb
where candidates_json is null;

alter table if exists routing_decisions
  alter column user_facing_error_json set not null,
  alter column candidates_json set not null;
