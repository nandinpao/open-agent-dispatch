-- Run as a PostgreSQL administrator. Replace passwords through a secret manager.
-- Flyway owns DDL; the runtime role receives DML only and must never own IAM tables.
-- Phase 3E uses PostgreSQL 18 core SHA-256 and requires no database extension.

do $$
begin
  if not exists (select 1 from pg_roles where rolname='opendispatch_migration') then
    create role opendispatch_migration login nosuperuser nocreatedb nocreaterole noinherit nobypassrls;
  end if;
  if not exists (select 1 from pg_roles where rolname='opendispatch_runtime') then
    create role opendispatch_runtime login nosuperuser nocreatedb nocreaterole noinherit nobypassrls;
  end if;
end $$;

do $$ begin execute format('grant connect on database %I to opendispatch_migration, opendispatch_runtime', current_database()); end $$;

-- Phase 5J archive tables remain in public under a reserved p5j_arc_*
-- namespace and have PUBLIC/runtime privileges explicitly revoked at archive time.
-- No database-level CREATE privilege or auxiliary schema is required.

alter schema public owner to opendispatch_migration;
grant usage on schema public to opendispatch_migration, opendispatch_runtime;
grant create on schema public to opendispatch_migration;

grant select, insert, update, delete on all tables in schema public to opendispatch_runtime;
grant usage, select on all sequences in schema public to opendispatch_runtime;
alter default privileges for role opendispatch_migration in schema public
  grant select, insert, update, delete on tables to opendispatch_runtime;
alter default privileges for role opendispatch_migration in schema public
  grant usage, select on sequences to opendispatch_runtime;

alter role opendispatch_runtime set row_security = on;
revoke create on schema public from opendispatch_runtime;
