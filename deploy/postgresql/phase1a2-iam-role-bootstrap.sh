#!/usr/bin/env sh
set -eu

: "${POSTGRES_HOST:=postgres}"
: "${POSTGRES_PORT:=5432}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
: "${IAM_MIGRATION_DB_PASSWORD:?IAM_MIGRATION_DB_PASSWORD is required}"
: "${IAM_RUNTIME_DB_PASSWORD:?IAM_RUNTIME_DB_PASSWORD is required}"
: "${IAM_MIGRATION_DB_USER:=opendispatch_migration}"
: "${IAM_RUNTIME_DB_USER:=opendispatch_runtime}"
[ "$IAM_MIGRATION_DB_USER" = "opendispatch_migration" ] || { echo "Phase 1A-2 requires IAM_MIGRATION_DB_USER=opendispatch_migration" >&2; exit 1; }
[ "$IAM_RUNTIME_DB_USER" = "opendispatch_runtime" ] || { echo "Phase 1A-2 requires IAM_RUNTIME_DB_USER=opendispatch_runtime" >&2; exit 1; }

export PGPASSWORD="$POSTGRES_PASSWORD"
psql --host "$POSTGRES_HOST" --port "$POSTGRES_PORT" --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 \
  --set migration_password="$IAM_MIGRATION_DB_PASSWORD" \
  --set runtime_password="$IAM_RUNTIME_DB_PASSWORD" \
  --set database_name="$POSTGRES_DB" <<'SQL'
-- PostgreSQL 18 core hashing is used by Phase 3E; no database extension is required.

do $$
begin
  if not exists (select 1 from pg_roles where rolname='opendispatch_migration') then
    create role opendispatch_migration login nosuperuser nocreatedb nocreaterole noinherit nobypassrls;
  end if;
  if not exists (select 1 from pg_roles where rolname='opendispatch_runtime') then
    create role opendispatch_runtime login nosuperuser nocreatedb nocreaterole noinherit nobypassrls;
  end if;
end $$;

alter role opendispatch_migration password :'migration_password';
alter role opendispatch_runtime password :'runtime_password';
alter role opendispatch_runtime set row_security = on;

-- Phase 5J archive storage is intentionally self-contained in public.
-- Detached archive tables are renamed and stripped of runtime/public privileges
-- by the SECURITY DEFINER retention function; no extra database schema is required.

do $$
declare object_row record;
begin
  for object_row in
    select n.nspname, c.relname, c.relkind
      from pg_class c join pg_namespace n on n.oid=c.relnamespace
     where n.nspname='public' and c.relkind in ('r','p','f','S','v','m')
  loop
    execute case object_row.relkind
      when 'S' then format('alter sequence %I.%I owner to opendispatch_migration', object_row.nspname, object_row.relname)
      when 'v' then format('alter view %I.%I owner to opendispatch_migration', object_row.nspname, object_row.relname)
      when 'm' then format('alter materialized view %I.%I owner to opendispatch_migration', object_row.nspname, object_row.relname)
      else format('alter table %I.%I owner to opendispatch_migration', object_row.nspname, object_row.relname)
    end;
  end loop;

  for object_row in
    select p.oid::regprocedure as signature
      from pg_proc p join pg_namespace n on n.oid=p.pronamespace
     where n.nspname='public' and p.prokind='f'
       and not exists (select 1 from pg_depend d where d.objid=p.oid and d.deptype='e')
  loop
    execute format('alter function %s owner to opendispatch_migration', object_row.signature);
  end loop;
end $$;

alter schema public owner to opendispatch_migration;
grant connect on database :"database_name" to opendispatch_migration, opendispatch_runtime;
grant usage on schema public to opendispatch_migration, opendispatch_runtime;
grant create on schema public to opendispatch_migration;
revoke create on schema public from opendispatch_runtime;
-- pg_catalog.sha256(bytea) is a PostgreSQL 18 core function and requires no extension grant.
grant select, insert, update, delete on all tables in schema public to opendispatch_runtime;
grant usage, select on all sequences in schema public to opendispatch_runtime;
alter default privileges for role opendispatch_migration in schema public
  grant select, insert, update, delete on tables to opendispatch_runtime;
alter default privileges for role opendispatch_migration in schema public
  grant usage, select on sequences to opendispatch_runtime;

-- Fail the administrator bootstrap itself unless the role separation boundary
-- required by Flyway/runtime is materially present in the target database.
do $$
declare
  public_owner text;
begin
  select pg_get_userbyid(n.nspowner)
    into public_owner
    from pg_namespace n
   where n.nspname='public';

  if public_owner <> 'opendispatch_migration' then
    raise exception using errcode='42501', message='OPENDISPATCH_PUBLIC_SCHEMA_OWNER_INVALID';
  end if;
  if not has_schema_privilege('opendispatch_migration','public','USAGE')
     or not has_schema_privilege('opendispatch_migration','public','CREATE') then
    raise exception using errcode='42501', message='OPENDISPATCH_MIGRATION_PUBLIC_PRIVILEGE_MISSING';
  end if;
  if has_schema_privilege('opendispatch_runtime','public','CREATE') then
    raise exception using errcode='42501', message='OPENDISPATCH_RUNTIME_PUBLIC_CREATE_FORBIDDEN';
  end if;
  if exists(select 1 from pg_roles where rolname in ('opendispatch_migration','opendispatch_runtime') and rolbypassrls) then
    raise exception using errcode='42501', message='OPENDISPATCH_ROLE_BYPASSRLS_FORBIDDEN';
  end if;
end $$;

select 'OPENDISPATCH_ROLE_BOOTSTRAP_COMPLETE public owner=opendispatch_migration' as bootstrap_status;
SQL
