package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof for C0-B6 protected peer-error mapping and canonical runtime disposition. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B6PeerErrorMappingContainerTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b6").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc; private A2APeerErrorMappingService service; private A2ARemoteReadExecutionQueueService queue;

    @BeforeEach void setUp(){DataSource ds=dataSource();jdbc=new JdbcTemplate(ds);NamedParameterJdbcTemplate named=new NamedParameterJdbcTemplate(ds);service=new A2APeerErrorMappingService(named);queue=new A2ARemoteReadExecutionQueueService(named);recreate();}

    @Test void protectedHttpAuthorizationCannotBeDowngradedToTemporary(){
        assertThatThrownBy(()->service.upsertDraft("tenant-a","peer-a","map-auth",null,"SEND_MESSAGE","AUTH_REQUIRED",403,"TEMPORARY","A2A_REMOTE_AUTH_RETRY","unsafe"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("A2A_PROTECTED_ERROR_CLASS_REMAP_FORBIDDEN");
    }

    @Test void protectedRemoteAuthorizationCodeCannotBeDowngradedEvenAtHttp400(){
        assertThatThrownBy(()->service.upsertDraft("tenant-a","peer-a","map-auth-code",null,"GET_TASK","AUTHORIZATION_DENIED",400,"TEMPORARY","A2A_REMOTE_AUTHZ_RETRY","unsafe"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("A2A_PROTECTED_ERROR_CLASS_REMAP_FORBIDDEN");
    }

    @Test void exactNonProtectedPeerOverrideCanMapBusyToRetry(){
        service.upsertDraft("tenant-a","peer-a","map-busy",null,"SEND_MESSAGE","BUSY",400,"TEMPORARY","A2A_REMOTE_BUSY","controlled peer mapping");
        service.activate("tenant-a","peer-a","map-busy");
        var r=service.resolveAndRecord("tenant-a","exec-a",null,"peer-a","if-a","SEND_MESSAGE",400,Map.of("error",Map.of("code","BUSY","message","retry later")),null);
        assertThat(r.baselineErrorClass()).isEqualTo("INVALID_REQUEST");
        assertThat(r.resolvedErrorClass()).isEqualTo("TEMPORARY");
        assertThat(r.disposition()).isEqualTo("RETRY");
        assertThat(r.mappingSource()).isEqualTo("PEER_OVERRIDE");
        assertThat(r.canonicalErrorCode()).isEqualTo("A2A_REMOTE_BUSY");
        assertThat(jdbc.queryForObject("select count(*) from a2a_peer_error_resolution_evidence",Integer.class)).isEqualTo(1);
    }

    @Test void interfaceOverrideBeatsPeerOverride(){
        service.upsertDraft("tenant-a","peer-a","map-peer",null,"GET_TASK","BUSY",400,"UNAVAILABLE","A2A_REMOTE_BUSY_PEER","peer default");service.activate("tenant-a","peer-a","map-peer");
        service.upsertDraft("tenant-a","peer-a","map-if","if-a","GET_TASK","BUSY",400,"TEMPORARY","A2A_REMOTE_BUSY_INTERFACE","interface override");service.activate("tenant-a","peer-a","map-if");
        var r=service.resolveAndRecord("tenant-a","exec-a","track-a","peer-a","if-a","GET_TASK",400,Map.of("errorCode","BUSY"),"busy");
        assertThat(r.overrideId()).isEqualTo("map-if");assertThat(r.canonicalErrorCode()).isEqualTo("A2A_REMOTE_BUSY_INTERFACE");
    }

    @Test void protectedRuntimeErrorBlocksExecutionInsteadOfRetrying(){
        var r=service.resolveAndRecord("tenant-a","exec-a",null,"peer-a","if-a","SEND_MESSAGE",403,Map.of("error",Map.of("code","DENIED","message","forbidden")),null);
        assertThat(r.resolvedErrorClass()).isEqualTo("AUTHORIZATION");assertThat(r.disposition()).isEqualTo("BLOCK");
        var outcome=queue.mappedFailure("tenant-a","exec-a",r,1,4);
        assertThat(outcome.status()).isEqualTo("BLOCKED");assertThat(outcome.completeNow()).isTrue();assertThat(outcome.retryScheduled()).isFalse();
        assertThat(jdbc.queryForObject("select status from a2a_remote_read_executions where tenant_id='tenant-a' and execution_id='exec-a'",String.class)).isEqualTo("BLOCKED");
    }

    private void recreate(){
        jdbc.execute("drop table if exists a2a_peer_error_resolution_evidence,a2a_peer_error_mapping_overrides,a2a_remote_read_executions,a2a_peer_interfaces,a2a_peer_registrations cascade");
        jdbc.execute("create table a2a_peer_registrations(tenant_id text not null,peer_id text not null,status text not null,primary key(tenant_id,peer_id))");
        jdbc.execute("create table a2a_peer_interfaces(tenant_id text not null,interface_id text not null,peer_id text not null,primary key(tenant_id,interface_id))");
        jdbc.execute("""
          create or replace function a2a_peer_error_protected_class(p_class text) returns boolean language sql immutable as $$
            select upper(coalesce(p_class,'')) in ('AUTHENTICATION','AUTHORIZATION','TRUST','RESIDENCY','SECURITY') $$
          """);
        jdbc.execute("""
          create or replace function a2a_peer_error_baseline_class(p_http_status integer,p_remote_error_code text) returns text language sql immutable as $$
            select case when p_http_status=401 then 'AUTHENTICATION' when p_http_status=403 then 'AUTHORIZATION'
              when upper(coalesce(p_remote_error_code,'')) like 'AUTHORIZATION%' then 'AUTHORIZATION'
              when upper(coalesce(p_remote_error_code,'')) like 'AUTHENTICATION%' then 'AUTHENTICATION'
              when upper(coalesce(p_remote_error_code,'')) like 'TRUST%' then 'TRUST'
              when upper(coalesce(p_remote_error_code,'')) like 'RESIDENCY%' then 'RESIDENCY'
              when upper(coalesce(p_remote_error_code,'')) like 'SECURITY%' then 'SECURITY'
              when p_http_status=429 then 'THROTTLED' when p_http_status in (408,425) then 'TEMPORARY' when p_http_status=404 then 'NOT_FOUND' when p_http_status=409 then 'CONFLICT'
              when p_http_status in (400,405,406,415,422) then 'INVALID_REQUEST' when p_http_status between 500 and 599 then 'UNAVAILABLE' when p_http_status between 400 and 499 then 'REMOTE_FAILURE' when nullif(btrim(coalesce(p_remote_error_code,'')),'') is not null then 'REMOTE_FAILURE' else 'UNKNOWN' end $$
          """);
        jdbc.execute("create or replace function a2a_peer_error_disposition(p_class text) returns text language sql immutable as $$ select case when upper(p_class) in ('AUTHENTICATION','AUTHORIZATION','TRUST','RESIDENCY','SECURITY') then 'BLOCK' when upper(p_class) in ('THROTTLED','TEMPORARY','UNAVAILABLE') then 'RETRY' when upper(p_class) in ('NOT_FOUND','CONFLICT') then 'RECONCILE' else 'TERMINAL' end $$");
        jdbc.execute("create table a2a_peer_error_mapping_overrides(tenant_id text not null,override_id text not null,peer_id text not null,interface_id text,operation_scope text not null,remote_error_code text not null,http_status int,canonical_error_class text not null,canonical_error_code text not null,reason text not null,override_status text not null,created_by text,created_at timestamptz default now(),activated_at timestamptz,retired_at timestamptz,updated_at timestamptz default now(),primary key(tenant_id,override_id))");
        jdbc.execute("create unique index uq_test_error_override_active on a2a_peer_error_mapping_overrides(tenant_id,peer_id,coalesce(interface_id,''),operation_scope,remote_error_code,coalesce(http_status,0)) where override_status='ACTIVE'");
        jdbc.execute("""
          create or replace function a2a_peer_error_resolve(p_tenant text,p_peer text,p_interface text,p_operation text,p_http_status integer,p_remote_error_code text)
          returns table(baseline_error_class text,resolved_error_class text,canonical_error_code text,error_disposition text,mapping_source text,override_id text,protected_baseline boolean)
          language sql stable as $$
            with b as(select a2a_peer_error_baseline_class(p_http_status,p_remote_error_code) c),o as(
              select x.* from a2a_peer_error_mapping_overrides x,b where x.tenant_id=p_tenant and x.peer_id=p_peer and x.override_status='ACTIVE' and x.remote_error_code=upper(btrim(coalesce(p_remote_error_code,''))) and (x.interface_id is null or x.interface_id=p_interface) and (x.operation_scope='ANY' or x.operation_scope=upper(coalesce(p_operation,''))) and (x.http_status is null or x.http_status=p_http_status) and (not a2a_peer_error_protected_class(b.c) or x.canonical_error_class=b.c) order by (x.interface_id is not null) desc,(x.operation_scope<>'ANY') desc,(x.http_status is not null) desc limit 1)
            select b.c,coalesce(o.canonical_error_class,b.c),coalesce(o.canonical_error_code,'A2A_REMOTE_'||b.c),a2a_peer_error_disposition(coalesce(o.canonical_error_class,b.c)),case when o.override_id is null then 'BASELINE' else 'PEER_OVERRIDE' end,o.override_id,a2a_peer_error_protected_class(b.c) from b left join o on true $$
          """);
        jdbc.execute("create table a2a_peer_error_resolution_evidence(tenant_id text not null,evidence_id text not null,execution_id text,tracking_id text,peer_id text not null,interface_id text not null,operation_scope text not null,http_status int,remote_error_code text,remote_error_message text,baseline_error_class text,resolved_error_class text,canonical_error_code text,error_disposition text,mapping_source text,override_id text,protected_baseline boolean,observed_at timestamptz default now(),primary key(tenant_id,evidence_id))");
        jdbc.execute("create table a2a_remote_read_executions(tenant_id text not null,execution_id text not null,status text not null,error_code text,error_message text,remote_error_code text,remote_error_message text,canonical_error_class text,error_disposition text,error_mapping_source text,error_mapping_override_id text,next_attempt_at timestamptz,claimed_by text,claim_until timestamptz,updated_at timestamptz,primary key(tenant_id,execution_id))");
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-a','ACTIVE')");jdbc.update("insert into a2a_peer_interfaces values('tenant-a','if-a','peer-a')");jdbc.update("insert into a2a_remote_read_executions(tenant_id,execution_id,status) values('tenant-a','exec-a','PROCESSING')");
    }
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
