package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL proof for C0-B7 governed automated Interface Conformance. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B7InterfaceConformanceContainerTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b7").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc; private A2AInterfaceConformanceService conformance; private A2APeerInterfaceContractService contracts;

    @BeforeEach void setUp(){DataSource ds=dataSource();jdbc=new JdbcTemplate(ds);var named=new NamedParameterJdbcTemplate(ds);var mapper=new ObjectMapper();conformance=new A2AInterfaceConformanceService(named,mapper);contracts=new A2APeerInterfaceContractService(named,mapper);recreate();}

    @Test void profileCannotWeakenMandatoryBaselineChecks(){
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->conformance.upsertDraft("tenant-a","profile-a","Baseline","",List.of("PROTOCOL_BINDING"),3600,3600));
        assertThat(ex.getMessage()).isEqualTo("A2A_CONFORMANCE_BASELINE_CHECKS_REQUIRED");
    }

    @Test void humanCannotWritePassAndProjectionRequiresGovernedRun(){
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->contracts.operationalState("tenant-a","if-a","HEALTHY","PASS","CLOSED"));
        assertThat(ex.getMessage()).isEqualTo("A2A_INTERFACE_CONFORMANCE_WRITE_REQUIRES_C0_B7");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update a2a_peer_interfaces set conformance_status='PASS' where tenant_id='tenant-a' and interface_id='if-a'"));
    }

    @Test void platformAutomatedRunCreatesAppendOnlyEvidenceAndCurrentPass(){
        makeInterfaceReady();activateBaseline("profile-a");matchingCard();
        Map<String,Object> result=conformance.run("tenant-a","if-a");
        assertThat(result.get("effectiveConformanceStatus")).isEqualTo("PASS");
        assertThat(jdbc.queryForObject("select count(*) from a2a_interface_conformance_evidence where tenant_id='tenant-a'",Integer.class)).isEqualTo(A2AInterfaceConformanceService.BASELINE_CHECKS.size());
        assertThat(jdbc.queryForObject("select a2a_interface_current_contract_eligible('tenant-a','if-a')",Boolean.class)).isTrue();
        String run=jdbc.queryForObject("select conformance_run_id from a2a_peer_interfaces where tenant_id='tenant-a' and interface_id='if-a'",String.class);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update a2a_interface_conformance_evidence set check_status='FAIL' where tenant_id='tenant-a' and run_id=?",run));
    }

    @Test void agentCardMismatchFailsConformanceAndSuspendsProviderLink(){
        makeInterfaceReady();activateBaseline("profile-a");
        jdbc.update("insert into a2a_peer_provider_links values('tenant-a','provider-a','peer-a','if-a','ACTIVE',now(),now())");
        jdbc.update("insert into a2a_agent_card_snapshots values('tenant-a','snap-a','peer-a',?::jsonb,'sha',null,now())","{\"supportedInterfaces\":[{\"url\":\"https://other.example/a2a\",\"protocolBinding\":\"HTTP+JSON\",\"protocolVersion\":\"1.0\"}]}");
        Map<String,Object> result=conformance.run("tenant-a","if-a");
        assertThat(result.get("effectiveConformanceStatus")).isEqualTo("FAIL");
        assertThat(jdbc.queryForObject("select status from a2a_peer_provider_links where tenant_id='tenant-a' and provider_id='provider-a'",String.class)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("select a2a_interface_current_contract_eligible('tenant-a','if-a')",Boolean.class)).isFalse();
    }

    @Test void activatingNewProfileInvalidatesPreviousPassAndSuspendsLink(){
        makeInterfaceReady();activateBaseline("profile-a");matchingCard();conformance.run("tenant-a","if-a");
        jdbc.update("insert into a2a_peer_provider_links values('tenant-a','provider-a','peer-a','if-a','ACTIVE',now(),now())");
        conformance.upsertDraft("tenant-a","profile-b","New baseline","rotation",A2AInterfaceConformanceService.BASELINE_CHECKS,7200,3600);
        conformance.activateProfile("tenant-a","profile-b");
        assertThat(jdbc.queryForObject("select a2a_interface_conformance_effective_status('tenant-a','if-a')",String.class)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("select status from a2a_peer_provider_links where tenant_id='tenant-a' and provider_id='provider-a'",String.class)).isEqualTo("SUSPENDED");
    }

    private void makeInterfaceReady(){
        contracts.configure("tenant-a","if-a","TW",List.of("MTLS"),List.of("stream-v1"),List.of("stream-v1"),"EXTERNAL_HTTP_DEFAULT",20);
        contracts.operationalState("tenant-a","if-a","HEALTHY",null,"CLOSED");
    }
    private void activateBaseline(String id){conformance.upsertDraft("tenant-a",id,"F0 READ Baseline","platform automated",A2AInterfaceConformanceService.BASELINE_CHECKS,3600,3600);conformance.activateProfile("tenant-a",id);}
    private void matchingCard(){jdbc.update("insert into a2a_agent_card_snapshots values('tenant-a','snap-a','peer-a',?::jsonb,'sha',null,now())","{\"supportedInterfaces\":[{\"url\":\"https://peer.example/a2a\",\"protocolBinding\":\"HTTP+JSON\",\"protocolVersion\":\"1.0\"}]}");}

    private void recreate(){
        jdbc.execute("drop table if exists a2a_interface_conformance_evidence,a2a_interface_conformance_runs,a2a_interface_conformance_profiles,a2a_peer_provider_links,a2a_agent_card_snapshots,a2a_peer_interfaces,a2a_peer_registrations cascade");
        jdbc.execute("create table a2a_peer_registrations(tenant_id text not null,peer_id text not null,status text not null,primary key(tenant_id,peer_id))");
        jdbc.execute("create table a2a_agent_card_snapshots(tenant_id text not null,snapshot_id text not null,peer_id text not null,card_json jsonb not null,card_sha256 text not null,protocol_version text,fetched_at timestamptz not null,primary key(tenant_id,snapshot_id))");
        jdbc.execute("""
          create table a2a_peer_interfaces(tenant_id text not null,interface_id text not null,peer_id text not null,url text not null,protocol_binding text not null,protocol_version text not null,
            interface_tenant text,streaming_supported boolean not null default false,push_notifications_supported boolean not null default false,status text not null,trust_status text not null,
            endpoint_region text,security_scheme_refs_json jsonb not null default '[]',required_extensions_json jsonb not null default '[]',supported_extensions_json jsonb not null default '[]',
            outbound_destination_policy_ref text,priority int not null default 100,health_status text not null default 'UNKNOWN',conformance_status text not null default 'UNKNOWN',circuit_state text not null default 'CLOSED',
            conformance_profile_id text,conformance_run_id text,conformance_valid_until timestamptz,contract_version bigint not null default 1,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,interface_id))
          """);
        jdbc.execute("create table a2a_peer_provider_links(tenant_id text not null,provider_id text not null,peer_id text not null,interface_id text not null,status text not null,created_at timestamptz,updated_at timestamptz,primary key(tenant_id,provider_id))");
        jdbc.execute("create or replace function a2a_version_policy_allowed(text,text,text,text) returns boolean language sql stable as $$ select true $$");
        jdbc.execute("""
          create table a2a_interface_conformance_profiles(tenant_id text not null,profile_id text not null,display_name text not null,description text,required_checks_json jsonb not null,valid_for_seconds bigint not null,max_agent_card_age_seconds bigint not null,profile_status text not null,profile_version bigint not null,created_by text not null,created_at timestamptz not null default now(),activated_at timestamptz,retired_at timestamptz,updated_at timestamptz not null default now(),primary key(tenant_id,profile_id))
          """);
        jdbc.execute("create unique index uq_test_active_profile on a2a_interface_conformance_profiles(tenant_id) where profile_status='ACTIVE'");
        jdbc.execute("""
          create table a2a_interface_conformance_runs(tenant_id text not null,run_id text not null,interface_id text not null,peer_id text not null,profile_id text not null,profile_version bigint not null,required_checks_json jsonb not null,valid_for_seconds bigint not null,max_agent_card_age_seconds bigint not null,run_status text not null,outcome text not null,started_at timestamptz not null,completed_at timestamptz,valid_until timestamptz,summary_json jsonb not null,created_by text not null,primary key(tenant_id,run_id))
          """);
        jdbc.execute("""
          create table a2a_interface_conformance_evidence(tenant_id text not null,evidence_id text not null,run_id text not null,interface_id text not null,check_code text not null,check_status text not null,expected_json jsonb not null,observed_json jsonb not null,evidence_ref text,evidence_digest text,evidence_source text not null,created_at timestamptz not null,primary key(tenant_id,evidence_id),unique(tenant_id,run_id,check_code))
          """);
        jdbc.execute("create or replace function prevent_test_conformance_evidence_mutation() returns trigger language plpgsql as $$ begin raise exception 'C0_B7_CONFORMANCE_EVIDENCE_APPEND_ONLY'; end $$");
        jdbc.execute("create trigger trg_test_evidence before update or delete on a2a_interface_conformance_evidence for each row execute function prevent_test_conformance_evidence_mutation()");
        jdbc.execute("""
          create or replace function a2a_interface_conformance_effective_status(p_tenant text,p_interface text) returns text language sql stable as $$
            select coalesce((select case when r.run_status='COMPLETED' and r.outcome='PASS' and p.profile_status='ACTIVE' and r.valid_until>now() then 'PASS' when r.run_status='COMPLETED' and r.outcome='PASS' then 'EXPIRED' when r.run_status='COMPLETED' and r.outcome='FAIL' then 'FAIL' else 'UNKNOWN' end from a2a_peer_interfaces i left join a2a_interface_conformance_runs r on r.tenant_id=i.tenant_id and r.run_id=i.conformance_run_id left join a2a_interface_conformance_profiles p on p.tenant_id=i.tenant_id and p.profile_id=r.profile_id where i.tenant_id=p_tenant and i.interface_id=p_interface),'UNKNOWN')
          $$
          """);
        jdbc.execute("create or replace function a2a_interface_conformance_current_pass(text,text) returns boolean language sql stable as $$ select a2a_interface_conformance_effective_status($1,$2)='PASS' $$");
        jdbc.execute("""
          create or replace function test_guard_projection() returns trigger language plpgsql as $$ declare o text; p text; u timestamptz; begin
            if old.conformance_status is not distinct from new.conformance_status and old.conformance_profile_id is not distinct from new.conformance_profile_id and old.conformance_run_id is not distinct from new.conformance_run_id and old.conformance_valid_until is not distinct from new.conformance_valid_until then return new; end if;
            if new.conformance_status in ('PASS','FAIL') then
              if new.conformance_run_id is null or new.conformance_profile_id is null then raise exception 'C0_B7_CONFORMANCE_PROJECTION_REQUIRES_GOVERNED_RUN'; end if;
              select outcome,profile_id,valid_until into o,p,u from a2a_interface_conformance_runs where tenant_id=new.tenant_id and run_id=new.conformance_run_id and interface_id=new.interface_id and run_status='COMPLETED';
              if o is null or o<>new.conformance_status or p<>new.conformance_profile_id then raise exception 'C0_B7_CONFORMANCE_PROJECTION_RUN_MISMATCH'; end if;
              if new.conformance_status='PASS' and (u is null or u<=now()) then raise exception 'C0_B7_CONFORMANCE_PASS_REQUIRES_CURRENT_VALID_RUN'; end if;
            elsif new.conformance_status='UNKNOWN' then
              if new.conformance_run_id is not null or new.conformance_profile_id is not null or new.conformance_valid_until is not null then raise exception 'C0_B7_UNKNOWN_CONFORMANCE_PROJECTION_MUST_CLEAR_RUN'; end if;
            else raise exception 'C0_B7_CONFORMANCE_PROJECTION_WRITE_NOT_GOVERNED'; end if; return new; end $$
          """);
        jdbc.execute("create trigger trg_test_projection before update of conformance_status,conformance_profile_id,conformance_run_id,conformance_valid_until on a2a_peer_interfaces for each row execute function test_guard_projection()");
        jdbc.execute("""
          create or replace function a2a_interface_current_contract_eligible(p_tenant text,p_interface text) returns boolean language sql stable as $$
            select exists(select 1 from a2a_peer_interfaces i where i.tenant_id=p_tenant and i.interface_id=p_interface and i.status='APPROVED' and i.protocol_binding='HTTP+JSON' and a2a_version_policy_allowed(i.tenant_id,i.peer_id,i.interface_id,i.protocol_version) and nullif(btrim(i.endpoint_region),'') is not null and i.outbound_destination_policy_ref='EXTERNAL_HTTP_DEFAULT' and i.health_status='HEALTHY' and a2a_interface_conformance_current_pass(i.tenant_id,i.interface_id) and i.circuit_state='CLOSED' and i.supported_extensions_json @> i.required_extensions_json)
          $$
          """);
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-a','ACTIVE')");
        jdbc.update("insert into a2a_peer_interfaces(tenant_id,interface_id,peer_id,url,protocol_binding,protocol_version,status,trust_status) values('tenant-a','if-a','peer-a','https://peer.example/a2a','HTTP+JSON','1.0','APPROVED','INTEGRITY_RECORDED')");
    }
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
