package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
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

/** PostgreSQL proof for C0-B4 interface contract eligibility and fail-closed operational state. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B4A2APeerInterfaceContractContainerTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b4").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc; private A2APeerInterfaceContractService service;

    @BeforeEach void setUp(){DataSource ds=dataSource();jdbc=new JdbcTemplate(ds);service=new A2APeerInterfaceContractService(new NamedParameterJdbcTemplate(ds),new ObjectMapper());recreate();}

    @Test void unknownOrIncompleteInterfaceIsNotGrandfatheredIntoRuntimeEligibility(){
        assertThat(eligible()).isFalse();
        var explained=service.explain("tenant-a","if-a");
        assertThat(explained.get("currentContractEligible")).isEqualTo(false);
    }

    @Test void b4CannotSelfCertifyConformanceButFutureGovernedPassCompletesEligibility(){
        service.configure("tenant-a","if-a","TW",List.of("MTLS"),List.of("stream-v1"),List.of("stream-v1","push-v1"),"EXTERNAL_HTTP_DEFAULT",20);
        service.operationalState("tenant-a","if-a","HEALTHY",null,"CLOSED");
        assertThat(eligible()).isFalse();
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->service.operationalState("tenant-a","if-a","HEALTHY","PASS","CLOSED"));
        assertThat(ex.getMessage()).isEqualTo("A2A_INTERFACE_CONFORMANCE_WRITE_REQUIRES_C0_B7");
        jdbc.update("update a2a_peer_interfaces set conformance_status='PASS' where tenant_id='tenant-a' and interface_id='if-a'");
        assertThat(eligible()).isTrue();
    }

    @Test void unsupportedRequiredExtensionFailsClosed(){
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->service.configure(
                "tenant-a","if-a","TW",List.of(),List.of("needed"),List.of("other"),"EXTERNAL_HTTP_DEFAULT",100));
        assertThat(ex.getMessage()).isEqualTo("A2A_REQUIRED_EXTENSION_NOT_SUPPORTED");
        assertThat(eligible()).isFalse();
    }

    @Test void circuitOrConformanceDegradationSuspendsProviderLinkAndDoesNotDisablePeer(){
        service.configure("tenant-a","if-a","TW",List.of(),List.of(),List.of(),"EXTERNAL_HTTP_DEFAULT",100);
        service.operationalState("tenant-a","if-a","HEALTHY",null,"CLOSED");
        jdbc.update("update a2a_peer_interfaces set conformance_status='PASS' where tenant_id='tenant-a' and interface_id='if-a'");
        jdbc.update("insert into a2a_peer_provider_links values('tenant-a','provider-a','peer-a','if-a','ACTIVE',now(),now())");
        service.operationalState("tenant-a","if-a","HEALTHY",null,"OPEN");
        assertThat(eligible()).isFalse();
        assertThat(jdbc.queryForObject("select status from a2a_peer_provider_links where tenant_id='tenant-a' and provider_id='provider-a'",String.class)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("select status from a2a_peer_registrations where tenant_id='tenant-a' and peer_id='peer-a'",String.class)).isEqualTo("ACTIVE");
    }

    private boolean eligible(){return Boolean.TRUE.equals(jdbc.queryForObject("select a2a_interface_current_contract_eligible('tenant-a','if-a')",Boolean.class));}
    private void recreate(){
        jdbc.execute("drop table if exists a2a_peer_provider_links,a2a_peer_interfaces,a2a_peer_registrations cascade");
        jdbc.execute("create table a2a_peer_registrations(tenant_id text not null,peer_id text not null,status text not null,primary key(tenant_id,peer_id))");
        jdbc.execute("""
          create table a2a_peer_interfaces(tenant_id text not null,interface_id text not null,peer_id text not null,url text not null,protocol_binding text not null,protocol_version text not null,
            interface_tenant text,streaming_supported boolean not null default false,push_notifications_supported boolean not null default false,status text not null,trust_status text not null,
            endpoint_region text,security_scheme_refs_json jsonb not null default '[]',required_extensions_json jsonb not null default '[]',supported_extensions_json jsonb not null default '[]',
            outbound_destination_policy_ref text,priority int not null default 100,health_status text not null default 'UNKNOWN',conformance_status text not null default 'UNKNOWN',circuit_state text not null default 'CLOSED',contract_version bigint not null default 1,
            created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,interface_id))
          """);
        jdbc.execute("create table a2a_peer_provider_links(tenant_id text not null,provider_id text not null,peer_id text not null,interface_id text not null,status text not null,created_at timestamptz,updated_at timestamptz,primary key(tenant_id,provider_id))");
        jdbc.execute("create or replace function a2a_interface_conformance_effective_status(p_tenant text,p_interface text) returns text language sql stable as $$ select coalesce((select conformance_status from a2a_peer_interfaces where tenant_id=p_tenant and interface_id=p_interface),'UNKNOWN') $$");
        jdbc.execute("""
          create or replace function a2a_interface_current_contract_eligible(p_tenant text,p_interface text) returns boolean language sql stable as $$
            select exists(select 1 from a2a_peer_interfaces i where i.tenant_id=p_tenant and i.interface_id=p_interface and i.status='APPROVED' and i.protocol_binding='HTTP+JSON' and i.protocol_version='1.0'
              and nullif(btrim(i.endpoint_region),'') is not null and i.outbound_destination_policy_ref='EXTERNAL_HTTP_DEFAULT' and i.health_status='HEALTHY' and i.conformance_status='PASS' and i.circuit_state='CLOSED'
              and i.supported_extensions_json @> i.required_extensions_json)
          $$
          """);
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-a','ACTIVE')");
        jdbc.update("insert into a2a_peer_interfaces(tenant_id,interface_id,peer_id,url,protocol_binding,protocol_version,status,trust_status) values('tenant-a','if-a','peer-a','https://peer.example/a2a','HTTP+JSON','1.0','APPROVED','INTEGRITY_RECORDED')");
    }
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
