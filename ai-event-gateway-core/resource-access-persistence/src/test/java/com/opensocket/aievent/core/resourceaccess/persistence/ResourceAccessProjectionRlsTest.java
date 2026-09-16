package com.opensocket.aievent.core.resourceaccess.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class ResourceAccessProjectionRlsTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine");
    private DataSource ds;
    @BeforeEach void setup()throws Exception{
        ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("drop schema if exists public cascade");s.execute("create schema public");
            s.execute("do $$ begin if not exists(select 1 from pg_roles where rolname='opendispatch_runtime') then create role opendispatch_runtime nologin; end if; end $$");
            s.execute("create table tenants(tenant_id varchar(64) primary key)");
            s.execute("create table permission_definitions(permission_code varchar(160) primary key,resource_type varchar(96) not null,action_code varchar(96) not null,description text not null,risk_level varchar(24) not null default 'MEDIUM',active boolean not null default true,version integer not null default 1,allowed_scope_types varchar(32)[] not null default array['TENANT']::varchar[],system_managed boolean not null default true)");
            s.execute("create table reason_code_catalog(reason_code varchar(128) primary key,http_status integer,category varchar(64) not null,retryable boolean not null default false,message_template text not null,active boolean not null default true,version integer not null default 1)");
            s.execute("insert into permission_definitions(permission_code,resource_type,action_code,description) values('task.read','TASK','READ','Read task')");
            s.execute("insert into tenants values('TENANT-A'),('TENANT-B')");
            s.execute("create or replace function iam_current_tenant_id() returns varchar language plpgsql stable security invoker as $$ declare value text; begin value := nullif(btrim(current_setting('app.current_tenant_id',true)), ''); if value is null then raise exception 'TENANT_CONTEXT_REQUIRED' using errcode='42501'; end if; return value; end $$");
        }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("grant usage on schema public to opendispatch_runtime");
            s.execute("grant select on resource_catalog to opendispatch_runtime");
            s.execute("grant select,insert,update,delete on resource_descriptors,resource_ownership_snapshots,resource_security_epochs,resource_participants,resource_projection_outbox_events,resource_descriptor_reconciliations,resource_orphan_repairs,resource_ownership_transfer_audits,resource_scope_grants,resource_scope_grant_audits,resource_scope_denies,resource_scope_deny_audits,resource_visibility_policies,resource_visibility_fields,resource_principal_clearances,resource_security_state_mutation_audits,resource_access_tenant_security_epochs,resource_access_principal_security_epochs,resource_authorization_decisions,resource_shadow_decision_comparisons,integration_issue_attachment_metadata,resource_attachment_security_metadata,resource_attachment_security_audits,resource_runtime_authorization_leases,resource_runtime_authorization_lease_events,resource_attachment_content_handles,resource_attachment_download_audits,resource_export_authorizations,resource_export_artifact_commits,resource_background_authorization_audits,resource_access_review_campaigns,resource_access_review_items,resource_access_review_events,resource_orphan_repair_events,resource_access_shadow_acceptance_thresholds,resource_access_release_assessments,resource_access_release_evidence,resource_access_legacy_bypass_inventory,resource_access_rollout_transitions,resource_access_rollback_rehearsals,resource_scope_materialized_snapshots,resource_department_revision_cutovers,resource_department_revision_cutover_events,resource_runtime_late_result_quarantines,resource_runtime_late_result_quarantine_events,resource_access_requests,resource_access_request_audits,resource_scale_certification_runs,resource_scale_certification_evidence to opendispatch_runtime");
        }
    }
    @Test void tenantContextCannotReadOrWriteAnotherTenant()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");insert(s,"TENANT-A","TASK-A");
            assertThrows(SQLException.class,()->insert(s,"TENANT-B","TASK-B"));
            s.execute("reset role");
        }
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-B',false)");
            try(ResultSet r=s.executeQuery("select count(*) from resource_descriptors")){r.next();assertEquals(0,r.getLong(1));}
            s.execute("reset role");
        }
    }

    @Test void policyRowsAreTenantIsolated()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");
            s.execute("insert into resource_scope_grants(tenant_id,scope_grant_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,visibility_level,valid_from,grant_source,grant_reason,grant_state,idempotency_key,version,created_at,created_by,updated_at,updated_by) values('TENANT-A','g-a','USER','u-a','task.read','TASK','TENANT','TENANT-A','SUMMARY',now(),'MANUAL','test','DRAFT','idem-a',1,now(),'u-a',now(),'u-a')");
            assertThrows(SQLException.class,()->s.execute("insert into resource_scope_grants(tenant_id,scope_grant_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,visibility_level,valid_from,grant_source,grant_reason,grant_state,idempotency_key,version,created_at,created_by,updated_at,updated_by) values('TENANT-B','g-b','USER','u-b','task.read','TASK','TENANT','TENANT-B','SUMMARY',now(),'MANUAL','test','DRAFT','idem-b',1,now(),'u-b',now(),'u-b')"));
            s.execute("reset role");
        }
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-B',false)");
            try(ResultSet r=s.executeQuery("select count(*) from resource_scope_grants")){r.next();assertEquals(0,r.getLong(1));}
            s.execute("reset role");
        }
    }


    @Test void governedAccessRequestsAreTenantIsolated()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");
            s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");
            insert(s,"TENANT-A","TASK-REQUEST-A");
            s.execute("insert into resource_scope_grants(tenant_id,scope_grant_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,visibility_level,valid_from,valid_to,grant_source,grant_reason,grant_state,idempotency_key,version,created_at,created_by,updated_at,updated_by) values('TENANT-A','grant-request-a','USER','requester-a','task.read','TASK','RESOURCE','TASK-REQUEST-A','SUMMARY',now(),now()+interval '1 day','ACCESS_REQUEST','temporary request','PENDING_APPROVAL','grant-request-idem-a',2,now(),'requester-a',now(),'requester-a')");
            s.execute("insert into resource_access_requests(tenant_id,access_request_id,scope_grant_id,ui_action_id,resource_type,resource_id,resource_version_at_request,requested_visibility,requester_id,business_purpose,valid_from,valid_to,request_state,approved_by,idempotency_key,version,created_at,updated_at) values('TENANT-A','request-a','grant-request-a','task.detail.view','TASK','TASK-REQUEST-A',1,'SUMMARY','requester-a','temporary investigation',now(),now()+interval '1 day','PENDING_APPROVAL',null,'request-idem-a',2,now(),now())");
            assertThrows(SQLException.class,()->s.execute("insert into resource_access_requests(tenant_id,access_request_id,scope_grant_id,ui_action_id,resource_type,resource_id,resource_version_at_request,requested_visibility,requester_id,business_purpose,valid_from,valid_to,request_state,approved_by,idempotency_key,version,created_at,updated_at) values('TENANT-B','request-b','grant-request-b','task.detail.view','TASK','TASK-REQUEST-B',1,'SUMMARY','requester-b','cross tenant attempt',now(),now()+interval '1 day','PENDING_APPROVAL',null,'request-idem-b',2,now(),now())"));
            s.execute("reset role");
        }
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");
            s.execute("select set_config('app.current_tenant_id','TENANT-B',false)");
            try(ResultSet r=s.executeQuery("select count(*) from resource_access_requests")){r.next();assertEquals(0,r.getLong(1));}
            try(ResultSet r=s.executeQuery("select count(*) from resource_access_request_audits")){r.next();assertEquals(0,r.getLong(1));}
            s.execute("reset role");
        }
    }

    @Test void decisionEvidenceIsTenantIsolated()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");
            s.execute("insert into resource_authorization_decisions(tenant_id,decision_id,principal_type,principal_id,permission_code,resource_type,resource_id,requested_visibility,granted_visibility,effect,decision_mode,shadow_only,reason_codes,policy_catalog_version,policy_revision,global_security_epoch,tenant_security_epoch,principal_security_epoch,resource_security_epoch,department_tree_revision,descriptor_hash,request_channel,operation_phase,purpose,correlation_id,cacheable,cache_ttl_ms,evaluated_at) values('TENANT-A','d-a','USER','u-a','task.read','TASK','missing-resource','SUMMARY','NONE','DENY','FORMAL',false,'RESOURCE_SCOPE_NOT_MATCHED',1,1,0,0,0,0,0,'hash','REST','START','test','corr-a',false,0,now())");
            assertThrows(SQLException.class,()->s.execute("insert into resource_authorization_decisions(tenant_id,decision_id,principal_type,principal_id,permission_code,resource_type,resource_id,requested_visibility,granted_visibility,effect,decision_mode,shadow_only,reason_codes,policy_catalog_version,policy_revision,global_security_epoch,tenant_security_epoch,principal_security_epoch,resource_security_epoch,department_tree_revision,descriptor_hash,request_channel,operation_phase,purpose,correlation_id,cacheable,cache_ttl_ms,evaluated_at) values('TENANT-B','d-b','USER','u-b','task.read','TASK','missing-resource','SUMMARY','NONE','DENY','FORMAL',false,'RESOURCE_SCOPE_NOT_MATCHED',1,1,0,0,0,0,0,'hash','REST','START','test','corr-b',false,0,now())"));
            s.execute("reset role");
        }
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-B',false)");try(ResultSet r=s.executeQuery("select count(*) from resource_authorization_decisions")){r.next();assertEquals(0,r.getLong(1));}s.execute("reset role");}
    }

    @Test void backgroundAuthorizationEvidenceIsTenantIsolated()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");
            s.execute("insert into resource_background_authorization_audits(tenant_id,audit_id,authorization_id,job_code,principal_id,decision_ids_text,resource_refs_text,runtime_lease_ids_text,executable,purpose,correlation_id,evaluated_at,created_at) values('TENANT-A','bg-a','auth-a','issue-relay','svc-a','d-a','ISSUE_PROJECT_MAPPING:m-a','lease-a',true,'relay','corr-a',now(),now())");
            assertThrows(SQLException.class,()->s.execute("insert into resource_background_authorization_audits(tenant_id,audit_id,authorization_id,job_code,principal_id,decision_ids_text,resource_refs_text,runtime_lease_ids_text,executable,purpose,correlation_id,evaluated_at,created_at) values('TENANT-B','bg-b','auth-b','issue-relay','svc-b','d-b','ISSUE_PROJECT_MAPPING:m-b','lease-b',true,'relay','corr-b',now(),now())"));
            s.execute("reset role");
        }
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-B',false)");
            try(ResultSet r=s.executeQuery("select count(*) from resource_background_authorization_audits")){r.next();assertEquals(0,r.getLong(1));}
            s.execute("reset role");
        }
    }

    @Test void accessReviewEvidenceIsTenantIsolated()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");
            s.execute("insert into resource_access_review_campaigns(tenant_id,campaign_id,campaign_name,campaign_status,created_by,due_at,idempotency_key,version,created_at,updated_at) values('TENANT-A','review-a','Tenant A review','DRAFT','u-a',now()+interval '7 days','review-idem-a',1,now(),now())");
            assertThrows(SQLException.class,()->s.execute("insert into resource_access_review_campaigns(tenant_id,campaign_id,campaign_name,campaign_status,created_by,due_at,idempotency_key,version,created_at,updated_at) values('TENANT-B','review-b','Tenant B review','DRAFT','u-b',now()+interval '7 days','review-idem-b',1,now(),now())"));
            s.execute("reset role");
        }
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-B',false)");
            try(ResultSet r=s.executeQuery("select count(*) from resource_access_review_campaigns")){r.next();assertEquals(0,r.getLong(1));}
            s.execute("reset role");
        }
    }

    @Test void releaseEvidenceIsTenantIsolated()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");
            s.execute("insert into resource_access_release_evidence(tenant_id,evidence_id,evidence_type,evidence_status,command_name,started_at,completed_at,actor_id,correlation_id) values('TENANT-A','ev-a','MAVEN_FULL_REACTOR','PASS','mvn verify',now(),now(),'u-a','corr-a')");
            assertThrows(SQLException.class,()->s.execute("insert into resource_access_release_evidence(tenant_id,evidence_id,evidence_type,evidence_status,command_name,started_at,completed_at,actor_id,correlation_id) values('TENANT-B','ev-b','MAVEN_FULL_REACTOR','PASS','mvn verify',now(),now(),'u-b','corr-b')"));
            s.execute("reset role");
        }
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-B',false)");try(ResultSet r=s.executeQuery("select count(*) from resource_access_release_evidence")){r.next();assertEquals(0,r.getLong(1));}s.execute("reset role");}
    }

    @Test void p4raJScopeCutoverEvidenceIsTenantIsolated()throws Exception{
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("set role opendispatch_runtime");s.execute("select set_config('app.current_tenant_id','TENANT-A',false)");
            s.execute("insert into resource_department_revision_cutovers(tenant_id,cutover_id,department_revision,cutover_status,prepared_snapshot_count,prepared_by,activated_by,correlation_id,prepared_at,activated_at,version) values('TENANT-A','cut-a',0,'ACTIVE',0,'u-a','u-b','corr-a',now(),now(),1)");
            assertThrows(SQLException.class,()->s.execute("insert into resource_department_revision_cutovers(tenant_id,cutover_id,department_revision,cutover_status,prepared_snapshot_count,prepared_by,activated_by,correlation_id,prepared_at,activated_at,version) values('TENANT-B','cut-b',0,'ACTIVE',0,'u-a','u-b','corr-b',now(),now(),1)"));
            s.execute("reset role");
        }
    }

    private void insert(Statement s,String tenant,String id)throws SQLException{s.execute("""
        insert into resource_descriptors(tenant_id,resource_type,resource_id,resource_key,sensitivity_level,maximum_visibility,security_state,ownership_version,participant_version,resource_version,descriptor_authority,descriptor_hash)
        values ('%s','TASK','%s','%s','CONFIDENTIAL','STANDARD','NORMAL',1,0,1,'TASK_DOMAIN','hash-%s')
        """.formatted(tenant,id,id,id));}
}
