package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
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
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL proof for C0-B5 governed version decisions and explicit legacy compatibility. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B5A2AVersionPolicyContainerTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b5").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc; private A2AVersionPolicyService service;

    @BeforeEach void setUp(){DataSource ds=dataSource();jdbc=new JdbcTemplate(ds);service=new A2AVersionPolicyService(new NamedParameterJdbcTemplate(ds),new ObjectMapper());recreate();}

    @Test void activePolicySupportsCurrentVersionAndRejectsUnknownVersion(){
        var ok=service.evaluate("tenant-a","peer-a","if-a","1.0");
        assertThat(ok.decision()).isEqualTo("SUPPORTED");assertThat(ok.allowed()).isTrue();
        var reject=service.evaluate("tenant-a","peer-a","if-a","2.0");
        assertThat(reject.decision()).isEqualTo("REJECTED");assertThat(reject.allowed()).isFalse();
    }

    @Test void deprecatedOrOutOfRangeVersionRequiresExplicitScopedLegacyAllowlist(){
        service.upsertDraft("tenant-a","policy-v2","Version 2","migration","2.0","2.0","2.0",List.of("1.0"),List.of(
                Map.of("subjectType","PEER","peerId","peer-a","protocolVersion","1.0","reason","controlled migration","validUntil",OffsetDateTime.now().plusDays(2).toString())));
        service.activate("tenant-a","policy-v2");
        assertThat(service.evaluate("tenant-a","peer-a","if-a","1.0").decision()).isEqualTo("DEPRECATED_ALLOWED");
        assertThat(service.evaluate("tenant-a","peer-b","if-b","1.0").decision()).isEqualTo("REJECTED");
        assertThat(service.evaluate("tenant-a","peer-a","if-a","2.0").decision()).isEqualTo("SUPPORTED");
    }

    @Test void activatingPolicySuspendsProviderLinksWhoseDeclaredVersionIsRejected(){
        assertThat(jdbc.queryForObject("select status from a2a_peer_provider_links where provider_id='provider-a'",String.class)).isEqualTo("ACTIVE");
        service.upsertDraft("tenant-a","policy-v2","Version 2","cutover","2.0","2.0","2.0",List.of(),List.of());
        service.activate("tenant-a","policy-v2");
        assertThat(jdbc.queryForObject("select status from a2a_peer_provider_links where provider_id='provider-a'",String.class)).isEqualTo("SUSPENDED");
    }

    private void recreate(){
        jdbc.execute("drop table if exists a2a_version_policy_legacy_allowlist,a2a_version_policies,a2a_peer_provider_links,a2a_peer_interfaces,a2a_peer_registrations cascade");
        jdbc.execute("create table a2a_peer_registrations(tenant_id text not null,peer_id text not null,status text not null,primary key(tenant_id,peer_id))");
        jdbc.execute("create table a2a_peer_interfaces(tenant_id text not null,interface_id text not null,peer_id text not null,protocol_binding text not null,protocol_version text not null,status text not null,endpoint_region text,outbound_destination_policy_ref text,health_status text,conformance_status text,circuit_state text,required_extensions_json jsonb not null default '[]',supported_extensions_json jsonb not null default '[]',primary key(tenant_id,interface_id))");
        jdbc.execute("create table a2a_peer_provider_links(tenant_id text not null,provider_id text not null,peer_id text not null,interface_id text not null,status text not null,updated_at timestamptz,primary key(tenant_id,provider_id))");
        jdbc.execute("create or replace function a2a_protocol_version_valid(p_version text) returns boolean language sql immutable as $$ select coalesce(p_version ~ '^[0-9]{1,6}\\.[0-9]{1,6}$',false) $$");
        jdbc.execute("create or replace function a2a_protocol_version_ord(p_version text) returns bigint language sql immutable as $$ select case when a2a_protocol_version_valid(p_version) then split_part(p_version,'.',1)::bigint*10000000::bigint+split_part(p_version,'.',2)::bigint else null end $$");
        jdbc.execute("create table a2a_version_policies(tenant_id text not null,policy_id text not null,display_name text not null,description text,policy_status text not null,preferred_version text not null,min_accepted_version text not null,max_accepted_version text not null,deprecated_versions_json jsonb not null default '[]',compatibility_mode text not null,policy_version bigint not null default 1,activated_at timestamptz,retired_at timestamptz,created_by text,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,policy_id))");
        jdbc.execute("create unique index uq_test_active_version_policy on a2a_version_policies(tenant_id) where policy_status='ACTIVE'");
        jdbc.execute("create table a2a_version_policy_legacy_allowlist(tenant_id text not null,policy_id text not null,allowlist_id text not null,subject_type text not null,peer_id text not null,interface_id text,protocol_version text not null,reason text not null,valid_until timestamptz,created_by text,created_at timestamptz not null default now(),primary key(tenant_id,policy_id,allowlist_id))");
        jdbc.execute("""
          create or replace function a2a_version_policy_decision(p_tenant text,p_peer text,p_interface text,p_version text) returns text language sql stable as $$
            with p as (select * from a2a_version_policies where tenant_id=p_tenant and policy_status='ACTIVE' limit 1)
            select coalesce((select case
              when not a2a_protocol_version_valid(p_version) then 'REJECTED'
              when a2a_protocol_version_ord(p_version) between a2a_protocol_version_ord(min_accepted_version) and a2a_protocol_version_ord(max_accepted_version) and not jsonb_exists(deprecated_versions_json,p_version) then 'SUPPORTED'
              when exists(select 1 from a2a_version_policy_legacy_allowlist l where l.tenant_id=p_tenant and l.policy_id=p.policy_id and l.peer_id=p_peer and l.protocol_version=p_version and (l.valid_until is null or l.valid_until>now()) and ((l.subject_type='PEER' and l.interface_id is null) or (l.subject_type='INTERFACE' and l.interface_id=p_interface))) then 'DEPRECATED_ALLOWED'
              else 'REJECTED' end from p),'REJECTED') $$
          """);
        jdbc.execute("create or replace function a2a_version_policy_allowed(p_tenant text,p_peer text,p_interface text,p_version text) returns boolean language sql stable as $$ select a2a_version_policy_decision(p_tenant,p_peer,p_interface,p_version) in ('SUPPORTED','DEPRECATED_ALLOWED') $$");
        jdbc.execute("""
          create or replace function a2a_interface_current_contract_eligible(p_tenant text,p_interface text) returns boolean language sql stable as $$
            select exists(select 1 from a2a_peer_interfaces i where i.tenant_id=p_tenant and i.interface_id=p_interface and i.status='APPROVED' and i.protocol_binding='HTTP+JSON'
              and a2a_version_policy_allowed(i.tenant_id,i.peer_id,i.interface_id,i.protocol_version) and nullif(btrim(i.endpoint_region),'') is not null
              and i.outbound_destination_policy_ref='EXTERNAL_HTTP_DEFAULT' and i.health_status='HEALTHY' and i.conformance_status='PASS' and i.circuit_state='CLOSED' and i.supported_extensions_json @> i.required_extensions_json) $$
          """);
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-a','ACTIVE'),('tenant-a','peer-b','ACTIVE')");
        jdbc.update("insert into a2a_peer_interfaces values('tenant-a','if-a','peer-a','HTTP+JSON','1.0','APPROVED','TW','EXTERNAL_HTTP_DEFAULT','HEALTHY','PASS','CLOSED','[]','[]'),('tenant-a','if-b','peer-b','HTTP+JSON','1.0','APPROVED','TW','EXTERNAL_HTTP_DEFAULT','HEALTHY','PASS','CLOSED','[]','[]')");
        jdbc.update("insert into a2a_peer_provider_links values('tenant-a','provider-a','peer-a','if-a','ACTIVE',now())");
        jdbc.update("insert into a2a_version_policies(tenant_id,policy_id,display_name,policy_status,preferred_version,min_accepted_version,max_accepted_version,deprecated_versions_json,compatibility_mode,policy_version,activated_at) values('tenant-a','policy-v1','Version 1','ACTIVE','1.0','1.0','1.0','[]','EXPLICIT_LEGACY_ALLOWLIST',1,now())");
    }
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
