-- Phase 11: server-driven beginner Machine Access catalog.
-- Keeps technical scope/audience values out of normal forms while preserving canonical values at the API boundary.

create table if not exists iam_machine_scope_catalog (
  scope_code varchar(160) primary key,
  display_name varchar(160) not null,
  description varchar(1000) not null,
  display_order integer not null default 100,
  status varchar(24) not null default 'ACTIVE',
  version bigint not null default 1,
  constraint ck_iam_machine_scope_status check(status in('ACTIVE','RETIRED')),
  constraint ck_iam_machine_scope_version check(version>0)
);

grant select on iam_machine_scope_catalog to opendispatch_runtime;
revoke insert,update,delete on iam_machine_scope_catalog from opendispatch_runtime;

insert into iam_machine_scope_catalog(scope_code,display_name,description,display_order,status,version) values
('events.intake','Send business events','Submit governed ERP, MES, BPM or other business events to OpenDispatch Event Intake.',10,'ACTIVE',1)
on conflict(scope_code) do update set display_name=excluded.display_name,description=excluded.description,display_order=excluded.display_order,status=excluded.status,version=iam_machine_scope_catalog.version+1;

-- Phase 8C/9 canonical Event Intake audience and product. The older abstract integration audience remains for compatibility.
insert into iam_credential_audiences(audience_code,display_name,description,status,version) values
('opendispatch-event-api','Event Intake API','Short-lived Machine JWT audience for POST /api/events/intake.','ACTIVE',1)
on conflict(audience_code) do update set display_name=excluded.display_name,description=excluded.description,status=excluded.status,version=iam_credential_audiences.version+1;

insert into iam_credential_api_products(product_code,display_name,description,allowed_audiences,api_prefixes,status,version) values
('EVENT_INTAKE','Business Event Intake','Allow an approved external system to submit business events through the Machine JWT resource server.',array['opendispatch-event-api'],array['/api/events/'],'ACTIVE',1)
on conflict(product_code) do update set display_name=excluded.display_name,description=excluded.description,allowed_audiences=excluded.allowed_audiences,api_prefixes=excluded.api_prefixes,status=excluded.status,version=iam_credential_api_products.version+1;

comment on table iam_machine_scope_catalog is 'Phase 11 server-owned labels for Machine OAuth scopes. UI selects display labels; canonical scope codes remain API/domain values.';
