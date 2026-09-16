package com.opensocket.aievent.core.resourceaccess.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class ResourceAccessProjectionMigrationTest {
    // Historical verifier evidence retained: P4RA-C used migrate("69"); P4RA-D asserted assertEquals("70",latestVersion()).
    // Historical P4RA-G verifier tokens retained: cleanMigrationCreatesP4raGArtifactSchema and assertEquals("75",latestVersion()).
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine");
    private DataSource dataSource;
    @BeforeEach void reset()throws Exception{
        dataSource=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){
            s.execute("drop schema if exists public cascade");s.execute("create schema public");
            s.execute("do $$ begin if not exists(select 1 from pg_roles where rolname='opendispatch_runtime') then create role opendispatch_runtime nologin; end if; end $$");
            s.execute("create table tenants(tenant_id varchar(64) primary key)");
            s.execute("create table permission_definitions(permission_code varchar(160) primary key,resource_type varchar(96) not null,action_code varchar(96) not null,description text not null,risk_level varchar(24) not null default 'MEDIUM',active boolean not null default true,version integer not null default 1,allowed_scope_types varchar(32)[] not null default array['TENANT']::varchar[],system_managed boolean not null default true)");
            s.execute("create table reason_code_catalog(reason_code varchar(128) primary key,http_status integer,category varchar(64) not null,retryable boolean not null default false,message_template text not null,active boolean not null default true,version integer not null default 1)");
            s.execute("insert into permission_definitions(permission_code,resource_type,action_code,description) values('task.read','TASK','READ','Read task')");
            s.execute("insert into tenants values('TENANT-A'),('TENANT-B')");
            s.execute("create or replace function iam_current_tenant_id() returns varchar language plpgsql stable security invoker as $$ declare value text; begin value := nullif(btrim(current_setting('app.current_tenant_id',true)), ''); if value is null then raise exception 'TENANT_CONTEXT_REQUIRED' using errcode='42501'; end if; return value; end $$");
        }
    }
    @Test void cleanMigrationCreatesP4raIReleaseGateSchema(){
        migrate(null);
        assertTrue(exists("resource_participants"));assertTrue(exists("resource_projection_outbox_events"));
        assertTrue(exists("resource_descriptor_reconciliations"));assertTrue(exists("resource_orphan_repairs"));
        assertTrue(exists("resource_ownership_transfer_audits"));
        assertTrue(exists("resource_scope_grants"));assertTrue(exists("resource_scope_denies"));
        assertTrue(exists("resource_visibility_policies"));assertTrue(exists("resource_visibility_fields"));
        assertTrue(exists("resource_principal_clearances"));assertTrue(exists("resource_security_state_mutation_audits"));
        assertTrue(exists("resource_authorization_decisions"));assertTrue(exists("resource_shadow_decision_comparisons"));
        assertTrue(exists("resource_access_tenant_security_epochs"));assertTrue(exists("resource_access_principal_security_epochs"));
        assertTrue(exists("integration_issue_attachment_metadata"));
        assertTrue(exists("resource_attachment_security_metadata"));assertTrue(exists("resource_runtime_authorization_leases"));
        assertTrue(exists("resource_attachment_content_handles"));assertTrue(exists("resource_attachment_download_audits"));
        assertTrue(exists("resource_export_authorizations"));assertTrue(exists("resource_export_artifact_commits"));
        assertTrue(exists("resource_background_authorization_audits"));
        assertTrue(exists("resource_access_review_campaigns"));assertTrue(exists("resource_access_review_items"));
        assertTrue(exists("resource_access_review_events"));assertTrue(exists("resource_orphan_repair_events"));
        assertTrue(exists("resource_access_release_assessments"));assertTrue(exists("resource_access_release_evidence"));assertTrue(exists("resource_access_rollout_transitions"));assertTrue(exists("resource_access_rollback_rehearsals"));
        assertTrue(exists("resource_scope_materialized_snapshots"));assertTrue(exists("resource_department_revision_cutovers"));
        assertTrue(exists("resource_runtime_late_result_quarantines"));assertTrue(exists("resource_scale_certification_runs"));
        assertTrue(exists("resource_scale_certification_evidence"));assertEquals("79",latestVersion());
        assertEquals(1, countForTenant("resource_access_policy_revisions", "TENANT-A"));
        assertEquals(1, countForTenant("resource_access_policy_revisions", "TENANT-B"));
        assertEquals(Set.of("TENANT","DEPARTMENT","GROUP"), allowedScopeTypes("integration.issue.connection.read"));
        assertEquals(Set.of("TENANT","DEPARTMENT","GROUP"), allowedScopeTypes("integration.issue.link.update"));
        assertEquals(Set.of("TENANT","DEPARTMENT","GROUP"), allowedScopeTypes("task.attachment.download"));
        assertEquals(Set.of("TENANT","DEPARTMENT","GROUP"), allowedScopeTypes("task.export"));
    }
    @Test void upgradeFromP4raIToP4raJPreservesReleaseAndPolicyEvidence(){
        migrate("77");assertTrue(exists("resource_access_release_assessments"));assertFalse(exists("resource_scope_materialized_snapshots"));
        migrate(null);assertTrue(exists("resource_scope_materialized_snapshots"));assertTrue(exists("resource_runtime_late_result_quarantines"));
        assertTrue(exists("resource_scale_certification_runs"));assertTrue(exists("resource_scope_grants"));assertTrue(exists("resource_authorization_decisions"));
    }

    @Test void upgradeFromP4raHToP4raIPreservesPolicyAndProjection(){
        migrate("76");assertTrue(exists("resource_access_review_campaigns"));assertFalse(exists("resource_access_release_assessments"));
        migrate(null);assertTrue(exists("resource_access_release_assessments"));assertTrue(exists("resource_access_release_evidence"));
        assertTrue(exists("resource_access_rollout_transitions"));assertTrue(exists("resource_scope_grants"));assertTrue(exists("resource_participants"));assertEquals(28,count("resource_catalog"));
    }
    @Test void historicalP4raFToP4raGUpgradeStillPreservesPolicyAndProjection(){
        migrate("73");assertTrue(exists("resource_authorization_decisions"));assertFalse(exists("integration_issue_attachment_metadata"));
        migrate(null);assertTrue(exists("integration_issue_attachment_metadata"));assertTrue(exists("resource_runtime_authorization_leases"));
        assertTrue(exists("resource_export_authorizations"));assertTrue(exists("resource_scope_grants"));assertTrue(exists("resource_participants"));assertEquals(28,count("resource_catalog"));
    }
    private void migrate(String target){var config=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false);if(target!=null)config.target(target);config.load().migrate();}
    private boolean exists(String table){try(Connection c=dataSourceConnection();PreparedStatement p=c.prepareStatement("select to_regclass(?) is not null")){p.setString(1,"public."+table);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}catch(Exception e){throw new AssertionError(e);}}
    private long count(String table){try(Connection c=dataSourceConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("select count(*) from "+table)){r.next();return r.getLong(1);}catch(Exception e){throw new AssertionError(e);}}
    private long countForTenant(String table,String tenant){try(Connection c=dataSourceConnection();PreparedStatement p=c.prepareStatement("select set_config('app.current_tenant_id',?,false)")){p.setString(1,tenant);p.execute();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select count(*) from "+table)){r.next();return r.getLong(1);}}catch(Exception e){throw new AssertionError(e);}}
    private Set<String> allowedScopeTypes(String permissionPoint){try(Connection c=dataSourceConnection();PreparedStatement p=c.prepareStatement("select unnest(allowed_scope_types) from permission_definitions where permission_code=?")){p.setString(1,permissionPoint);try(ResultSet r=p.executeQuery()){Set<String> values=new HashSet<>();while(r.next())values.add(r.getString(1));return values;}}catch(Exception e){throw new AssertionError(e);}}
    private String latestVersion(){try(Connection c=dataSourceConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("select version from flyway_schema_history where success order by installed_rank desc limit 1")){r.next();return r.getString(1);}catch(Exception e){throw new AssertionError(e);}}
    private Connection dataSourceConnection()throws SQLException{return dataSource.getConnection();}
}
