package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/** PostgreSQL proof for C0-B2 evidence-set -> computed assurance grants. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B2TrustAssurancePolicyContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b2").withUsername("opendispatch").withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private A2APeerTrustEvidenceService evidence;
    private A2ATrustAssurancePolicyService assurance;

    @BeforeEach
    void setUp() {
        DataSource ds=dataSource(); jdbc=new JdbcTemplate(ds);
        ObjectMapper mapper=new ObjectMapper();
        evidence=new A2APeerTrustEvidenceService(new NamedParameterJdbcTemplate(ds),mapper);
        assurance=new A2ATrustAssurancePolicyService(new NamedParameterJdbcTemplate(ds),mapper);
        recreateSchema();
    }

    @Test
    void legacyTrustStatusCannotCreateAComputedGrant() {
        jdbc.update("update a2a_peer_registrations set trust_status='TRUST_APPROVED' where tenant_id='tenant-a' and peer_id='peer-a'");
        jdbc.update("update a2a_peer_interfaces set trust_status='TRUST_APPROVED' where tenant_id='tenant-a' and interface_id='if-a'");
        TrustAssuranceEvaluation peer=assurance.evaluate("tenant-a","peer-a",null,PeerTrustEvidenceSubjectType.PEER);
        assertThat(peer.activePolicyPresent()).isFalse();
        assertThat(peer.grants()).isEmpty();
        assertThrows(IllegalArgumentException.class,()->assurance.requireReadAllowed("tenant-a","peer-a","if-a"));
    }

    @Test
    void activeEvidenceSetsComputeReadGrantForPeerAndInterface() {
        createAndActivateReadPolicy();
        evidence.record("tenant-a","peer-a","PEER",null,"MANUAL_APPROVAL","admin","WORKFLOW","approval:1",null,Map.of(),null,null,null);
        evidence.record("tenant-a","peer-a","INTERFACE","if-a","MTLS_CHANNEL_BOUND","tls","TLS_VALIDATOR","tls:1",null,Map.of(),null,null,null);

        TrustAssuranceEvaluation peer=assurance.evaluate("tenant-a","peer-a",null,PeerTrustEvidenceSubjectType.PEER);
        TrustAssuranceEvaluation iface=assurance.evaluate("tenant-a","peer-a","if-a",PeerTrustEvidenceSubjectType.INTERFACE);
        assertThat(peer.grants()).containsExactly(PeerTrustAssuranceGrant.READ_ALLOWED);
        assertThat(iface.grants()).containsExactly(PeerTrustAssuranceGrant.READ_ALLOWED);
        assurance.requireReadAllowed("tenant-a","peer-a","if-a");
    }

    @Test
    void anyOfEvidenceSetsAreAlternativeButEachInnerSetIsAllOf() {
        assurance.upsertDraft("tenant-a","policy-any","Any Of",null,List.of(
                Map.of("grantType","READ_ALLOWED","subjectType","PEER","requiredEvidenceSets",List.of(
                        List.of("MANUAL_APPROVAL"),
                        List.of("CARD_SIGNATURE_VERIFIED","CONFORMANCE_CERTIFIED"))),
                Map.of("grantType","READ_ALLOWED","subjectType","INTERFACE","requiredEvidenceSets",List.of(List.of("MTLS_CHANNEL_BOUND")))
        ));
        assurance.activate("tenant-a","policy-any");
        evidence.record("tenant-a","peer-a","PEER",null,"CARD_SIGNATURE_VERIFIED","card","TEST",null,null,Map.of(),null,null,null);
        assertThat(assurance.evaluate("tenant-a","peer-a",null,PeerTrustEvidenceSubjectType.PEER).grants()).isEmpty();
        evidence.record("tenant-a","peer-a","PEER",null,"CONFORMANCE_CERTIFIED","suite","TEST",null,null,Map.of(),null,null,null);
        assertThat(assurance.evaluate("tenant-a","peer-a",null,PeerTrustEvidenceSubjectType.PEER).grants())
                .containsExactly(PeerTrustAssuranceGrant.READ_ALLOWED);
    }

    @Test
    void revocationRemovesGrantWithoutRewritingPolicy() {
        createAndActivateReadPolicy();
        Map<String,Object> peerEvidence=evidence.record("tenant-a","peer-a","PEER",null,"MANUAL_APPROVAL","admin","WORKFLOW","approval:2",null,Map.of(),null,null,null);
        evidence.record("tenant-a","peer-a","INTERFACE","if-a","MTLS_CHANNEL_BOUND","tls","TLS_VALIDATOR","tls:2",null,Map.of(),null,null,null);
        assurance.requireReadAllowed("tenant-a","peer-a","if-a");
        evidence.revoke("tenant-a","peer-a",String.valueOf(peerEvidence.get("evidence_id")),"withdrawn");
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->assurance.requireReadAllowed("tenant-a","peer-a","if-a"));
        assertThat(ex.getMessage()).isEqualTo("A2A_PEER_READ_ASSURANCE_REQUIRED");
    }

    private void createAndActivateReadPolicy(){
        assurance.upsertDraft("tenant-a","policy-read","Remote READ",null,List.of(
                Map.of("grantType","READ_ALLOWED","subjectType","PEER","requiredEvidenceSets",List.of(List.of("MANUAL_APPROVAL"))),
                Map.of("grantType","READ_ALLOWED","subjectType","INTERFACE","requiredEvidenceSets",List.of(List.of("MTLS_CHANNEL_BOUND")))
        ));
        assurance.activate("tenant-a","policy-read");
    }

    private void recreateSchema(){
        jdbc.execute("drop table if exists a2a_trust_assurance_rules,a2a_trust_assurance_policies,a2a_peer_trust_evidence,a2a_peer_interfaces,a2a_peer_registrations cascade");
        jdbc.execute("create table a2a_peer_registrations(tenant_id text not null,peer_id text not null,display_name text,agent_card_url text,trust_status text,status text,created_at timestamptz,updated_at timestamptz,primary key(tenant_id,peer_id))");
        jdbc.execute("create table a2a_peer_interfaces(tenant_id text not null,interface_id text not null,peer_id text not null,url text,protocol_binding text,protocol_version text,status text,trust_status text,created_at timestamptz,updated_at timestamptz,primary key(tenant_id,interface_id),foreign key(tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id))");
        jdbc.execute("""
            create table a2a_peer_trust_evidence(
              tenant_id text not null,evidence_id text not null,peer_id text not null,interface_id text,subject_type text not null,evidence_type text not null,
              evidence_status text not null,issuer text,source text not null,evidence_ref text,evidence_digest text,details_json jsonb not null,
              observed_at timestamptz not null,valid_from timestamptz not null,valid_until timestamptz,revoked_at timestamptz,revocation_reason text,
              created_by text not null,created_at timestamptz not null,primary key(tenant_id,evidence_id))
            """);
        jdbc.execute("""
            create table a2a_trust_assurance_policies(
              tenant_id text not null,policy_id text not null,display_name text not null,description text,policy_status text not null,policy_version bigint not null,
              created_by text not null,created_at timestamptz not null,activated_at timestamptz,retired_at timestamptz,updated_at timestamptz not null,
              primary key(tenant_id,policy_id))
            """);
        jdbc.execute("create unique index uq_c0b2_active on a2a_trust_assurance_policies(tenant_id) where policy_status='ACTIVE'");
        jdbc.execute("""
            create table a2a_trust_assurance_rules(
              tenant_id text not null,policy_id text not null,rule_id text not null,grant_type text not null,subject_type text not null,
              required_evidence_sets_json jsonb not null,description text,created_at timestamptz not null,
              primary key(tenant_id,policy_id,rule_id),foreign key(tenant_id,policy_id) references a2a_trust_assurance_policies(tenant_id,policy_id))
            """);
        jdbc.execute("""
            create or replace function a2a_has_assurance_grant(p_tenant text,p_peer text,p_interface text,p_subject text,p_grant text)
            returns boolean language sql stable as $$
              select exists (
                select 1 from a2a_trust_assurance_policies p join a2a_trust_assurance_rules r on r.tenant_id=p.tenant_id and r.policy_id=p.policy_id
                 where p.tenant_id=p_tenant and p.policy_status='ACTIVE' and r.subject_type=p_subject and r.grant_type=p_grant
                   and exists (select 1 from jsonb_array_elements(r.required_evidence_sets_json) s(value)
                     where not exists (select 1 from jsonb_array_elements_text(s.value) req(evidence_type)
                       where not exists (select 1 from a2a_peer_trust_evidence e
                         where e.tenant_id=p_tenant and e.peer_id=p_peer and e.subject_type=p_subject
                           and ((p_subject='PEER' and e.interface_id is null) or (p_subject='INTERFACE' and e.interface_id=p_interface))
                           and e.evidence_type=req.evidence_type and e.evidence_status='ACTIVE' and e.valid_from<=now()
                           and (e.valid_until is null or e.valid_until>now()))))
              )
            $$
            """);
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-a','Peer A','https://a.example/card','INTEGRITY_RECORDED','DRAFT',now(),now())");
        jdbc.update("insert into a2a_peer_interfaces values('tenant-a','if-a','peer-a','https://a.example/a2a','HTTP+JSON','1.0','APPROVED','INTEGRITY_RECORDED',now(),now())");
    }

    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
