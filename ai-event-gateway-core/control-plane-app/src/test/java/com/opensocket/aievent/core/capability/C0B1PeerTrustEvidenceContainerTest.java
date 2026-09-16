package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** PostgreSQL proof for C0-B1 multidimensional peer trust evidence. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B1PeerTrustEvidenceContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b1").withUsername("opendispatch").withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private A2APeerTrustEvidenceService service;

    @BeforeEach
    void setUp() {
        DataSource ds = dataSource();
        jdbc = new JdbcTemplate(ds);
        service = new A2APeerTrustEvidenceService(new NamedParameterJdbcTemplate(ds), new ObjectMapper());
        recreateSchema();
    }

    @Test
    void independentEvidenceTypesMayCoexistWithoutCreatingAnAssuranceGrant() {
        Map<String,Object> signature = service.record("tenant-a","peer-a","PEER",null,
                "CARD_SIGNATURE_VERIFIED","issuer-a","CARD_SIGNATURE_VALIDATOR","card:snapshot-1","sha256:abc",
                Map.of("algorithm","example"),null,null,null);
        Map<String,Object> mtls = service.record("tenant-a","peer-a","INTERFACE","if-a",
                "MTLS_CHANNEL_BOUND","issuer-b","TLS_CHANNEL_VALIDATOR","tls:session-1","sha256:def",
                Map.of("san","peer.example"),null,null,null);

        assertThat(signature.get("evidence_type")).isEqualTo("CARD_SIGNATURE_VERIFIED");
        assertThat(mtls.get("evidence_type")).isEqualTo("MTLS_CHANNEL_BOUND");
        assertThat(service.evidence("tenant-a","peer-a",true)).hasSize(2);
        assertThat(jdbc.queryForObject("select trust_status from a2a_peer_registrations where tenant_id='tenant-a' and peer_id='peer-a'",String.class))
                .isEqualTo("INTEGRITY_RECORDED");
    }

    @Test
    void revocationMustNotMutateTheEvidenceFact() {
        Map<String,Object> recorded = service.record("tenant-a","peer-a","PEER",null,
                "MANUAL_APPROVAL","admin-a","ADMIN_WORKFLOW","approval:1",null,Map.of(),null,null,null);
        String id = String.valueOf(recorded.get("evidence_id"));
        service.revoke("tenant-a","peer-a",id,"approval withdrawn");
        assertThat(service.evidence("tenant-a","peer-a",true)).isEmpty();
        assertThat(service.evidence("tenant-a","peer-a",false).getFirst().get("evidence_status")).isEqualTo("REVOKED");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update a2a_peer_trust_evidence set evidence_type='MTLS_CHANNEL_BOUND' where tenant_id='tenant-a' and evidence_id=?", id));
    }

    @Test
    void interfaceEvidenceMustBelongToTheSamePeer() {
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-b','Peer B','https://b.example/card','PROPOSED','DRAFT',now(),now())");
        jdbc.update("insert into a2a_peer_interfaces values('tenant-a','if-b','peer-b','https://b.example/a2a','HTTP+JSON','1.0','PROPOSED','PROPOSED')");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.record(
                "tenant-a","peer-a","INTERFACE","if-b","MTLS_CHANNEL_BOUND",null,"TEST",null,null,Map.of(),
                OffsetDateTime.now(ZoneOffset.UTC),null,null));
        assertThat(ex.getMessage()).isEqualTo("A2A_PEER_INTERFACE_NOT_FOUND");
    }

    private void recreateSchema() {
        jdbc.execute("drop table if exists a2a_peer_trust_evidence,a2a_peer_interfaces,a2a_peer_registrations cascade");
        jdbc.execute("create table a2a_peer_registrations(tenant_id text not null,peer_id text not null,display_name text,agent_card_url text,trust_status text,status text,last_card_refresh_at timestamptz,created_at timestamptz,updated_at timestamptz,primary key(tenant_id,peer_id))");
        jdbc.execute("create table a2a_peer_interfaces(tenant_id text not null,interface_id text not null,peer_id text not null,url text,protocol_binding text,protocol_version text,status text,trust_status text,primary key(tenant_id,interface_id),foreign key(tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id))");
        jdbc.execute("""
            create table a2a_peer_trust_evidence(
              tenant_id text not null,evidence_id text not null,peer_id text not null,interface_id text,
              subject_type text not null,evidence_type text not null,evidence_status text not null,
              issuer text,source text not null,evidence_ref text,evidence_digest text,details_json jsonb not null,
              observed_at timestamptz not null,valid_from timestamptz not null,valid_until timestamptz,
              revoked_at timestamptz,revocation_reason text,created_by text not null,created_at timestamptz not null,
              primary key(tenant_id,evidence_id),foreign key(tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
              foreign key(tenant_id,interface_id) references a2a_peer_interfaces(tenant_id,interface_id),
              check ((subject_type='PEER' and interface_id is null) or (subject_type='INTERFACE' and interface_id is not null)),
              check (evidence_type in ('CARD_SIGNATURE_VERIFIED','MTLS_CHANNEL_BOUND','PRIVATE_NETWORK_ATTESTED','MANUAL_APPROVAL','CONFORMANCE_CERTIFIED','PROCESSING_ATTESTED')),
              check (evidence_status in ('ACTIVE','REVOKED')))
            """);
        jdbc.execute("""
            create or replace function protect_a2a_peer_trust_evidence_c0b1_test() returns trigger language plpgsql as $$
            begin
              if old.evidence_type is distinct from new.evidence_type or old.details_json is distinct from new.details_json then
                raise exception 'C0_B1_PEER_TRUST_EVIDENCE_FACT_IMMUTABLE' using errcode='55000';
              end if;
              return new;
            end $$
            """);
        jdbc.execute("create trigger trg_c0b1_test before update on a2a_peer_trust_evidence for each row execute function protect_a2a_peer_trust_evidence_c0b1_test()");
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-a','Peer A','https://a.example/card','INTEGRITY_RECORDED','DRAFT',null,now(),now())");
        jdbc.update("insert into a2a_peer_interfaces values('tenant-a','if-a','peer-a','https://a.example/a2a','HTTP+JSON','1.0','PROPOSED','INTEGRITY_RECORDED')");
    }

    private DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(POSTGRES.getDriverClassName());
        ds.setUrl(POSTGRES.getJdbcUrl()); ds.setUsername(POSTGRES.getUsername()); ds.setPassword(POSTGRES.getPassword());
        return ds;
    }
}
