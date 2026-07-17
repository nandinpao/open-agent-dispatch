-- Phase 20 live hotfix for DBs already created from older clean baselines.
-- The Gateway heartbeat mapper inserts a nullable node.version for heartbeat-only calls.
-- Keep the column nullable-safe by normalizing it to varchar with a non-null default.

alter table gateway_nodes add column if not exists version varchar(64);
alter table gateway_nodes alter column version type varchar(64) using version::text;
alter table gateway_nodes alter column version set default 'unknown';
update gateway_nodes set version = 'unknown' where version is null;
alter table gateway_nodes alter column version set not null;

alter table gateway_nodes add column if not exists node_name varchar(255);
alter table gateway_nodes add column if not exists host_name varchar(255);
alter table gateway_nodes add column if not exists advertise_host varchar(255);
alter table gateway_nodes add column if not exists http_port int;
alter table gateway_nodes add column if not exists ws_port int;
alter table gateway_nodes add column if not exists region varchar(64);
alter table gateway_nodes add column if not exists zone varchar(64);
alter table gateway_nodes add column if not exists site_id varchar(128);
alter table gateway_nodes add column if not exists registered_at timestamptz;
alter table gateway_nodes add column if not exists last_heartbeat_at timestamptz;
alter table gateway_nodes add column if not exists lease_expires_at timestamptz;

create index if not exists idx_gateway_nodes_lease on gateway_nodes(status, lease_expires_at);
