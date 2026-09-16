package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** PostgreSQL proof for C0-B3 processing/storage-region attestation governance. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B3PeerProcessingAttestationContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b3").withUsername("opendispatch").withPassword("opendispatch");

    private JdbcTemplate jdbc;
    private A2APeerTrustEvidenceService evidence;
    private A2ATrustAssurancePolicyService assurance;
    private A2APeerProcessingAttestationService attestations;

    @BeforeEach
    void setUp(){
        DataSource ds=dataSource(); jdbc=new JdbcTemplate(ds); ObjectMapper mapper=new ObjectMapper();
        evidence=new A2APeerTrustEvidenceService(new NamedParameterJdbcTemplate(ds),mapper);
        assurance=new A2ATrustAssurancePolicyService(new NamedParameterJdbcTemplate(ds),mapper);
        attestations=new A2APeerProcessingAttestationService(new NamedParameterJdbcTemplate(ds),mapper,evidence);
        recreateSchema();
    }

    @Test
    void pendingAssertionIsNotEvidenceAndGenericEvidenceApiCannotBypassVerification(){
        Map<String,Object> pending=record("ref:pending");
        assertThat(pending.get("attestation_status")).isEqualTo("PENDING");
        assertThat(evidence.evidence("tenant-a","peer-a",true)).isEmpty();
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->evidence.record(
                "tenant-a","peer-a","PEER",null,"PROCESSING_ATTESTED","issuer","TEST","direct",null,Map.of(),null,null,null));
        assertThat(ex.getMessage()).isEqualTo("A2A_PROCESSING_ATTESTATION_EVIDENCE_REQUIRES_C0_B3_VERIFICATION");
    }

    @Test
    void verificationCreatesLinkedProcessingEvidenceAndPolicyMayConsumeIt(){
        createProcessingPolicy();
        Map<String,Object> pending=record("ref:verified");
        String id=String.valueOf(pending.get("attestation_id"));
        Map<String,Object> verified=attestations.verify("tenant-a","peer-a",id,"reviewed");
        assertThat(verified.get("attestation_status")).isEqualTo("VERIFIED");
        assertThat(verified.get("trust_evidence_id")).isNotNull();
        assertThat(evidence.evidence("tenant-a","peer-a",true)).extracting(e->e.get("evidence_type"))
                .containsExactly("PROCESSING_ATTESTED");
        assertThat(assurance.evaluate("tenant-a","peer-a",null,PeerTrustEvidenceSubjectType.PEER).grants())
                .containsExactly(PeerTrustAssuranceGrant.SENSITIVE_READ_ALLOWED);
    }

    @Test
    void revokingVerifiedAttestationRevokesLinkedEvidenceAndRemovesGrant(){
        createProcessingPolicy();
        String id=String.valueOf(record("ref:revoke").get("attestation_id"));
        Map<String,Object> verified=attestations.verify("tenant-a","peer-a",id,null);
        String evidenceId=String.valueOf(verified.get("trust_evidence_id"));
        assertThat(assurance.evaluate("tenant-a","peer-a",null,PeerTrustEvidenceSubjectType.PEER).grants())
                .contains(PeerTrustAssuranceGrant.SENSITIVE_READ_ALLOWED);
        attestations.revoke("tenant-a","peer-a",id,"region assurance withdrawn");
        assertThat(assurance.evaluate("tenant-a","peer-a",null,PeerTrustEvidenceSubjectType.PEER).grants()).isEmpty();
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->evidence.revoke("tenant-a","peer-a",evidenceId,"direct"));
        assertThat(ex.getMessage()).isEqualTo("A2A_PROCESSING_ATTESTATION_EVIDENCE_REVOKE_REQUIRES_C0_B3");
    }

    @Test
    void onlyOneStoredVerifiedAttestationMayExistAndFactsAreImmutable(){
        String first=String.valueOf(record("ref:first").get("attestation_id"));
        attestations.verify("tenant-a","peer-a",first,null);
        String second=String.valueOf(record("ref:second").get("attestation_id"));
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->attestations.verify("tenant-a","peer-a",second,null));
        assertThat(ex.getMessage()).isEqualTo("A2A_CURRENT_PROCESSING_ATTESTATION_ALREADY_EXISTS");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update a2a_peer_processing_attestations set processing_region='OTHER' where tenant_id='tenant-a' and attestation_id=?",first));
    }

    private Map<String,Object> record(String ref){
        return attestations.record("tenant-a","peer-a","THIRD_PARTY_ATTESTED","TW","TW","auditor","AUDIT",ref,"sha256:abc",Map.of("scope","read"),OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
    }

    private void createProcessingPolicy(){
        assurance.upsertDraft("tenant-a","processing-policy","Processing Attestation",null,List.of(
                Map.of("grantType","SENSITIVE_READ_ALLOWED","subjectType","PEER","requiredEvidenceSets",List.of(List.of("PROCESSING_ATTESTED")))
        ));
        assurance.activate("tenant-a","processing-policy");
    }

    private void recreateSchema(){
        jdbc.execute("drop table if exists a2a_peer_processing_attestations,a2a_trust_assurance_rules,a2a_trust_assurance_policies,a2a_peer_trust_evidence,a2a_peer_interfaces,a2a_peer_registrations cascade");
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
            create table a2a_peer_processing_attestations(
              tenant_id text not null,attestation_id text not null,peer_id text not null,attestation_type text not null,processing_region text not null,storage_region text not null,
              issuer text not null,source text not null,evidence_ref text,evidence_digest text,details_json jsonb not null,attestation_status text not null,
              valid_from timestamptz not null,valid_until timestamptz,verified_at timestamptz,verified_by text,verification_note text,trust_evidence_id text,
              revoked_at timestamptz,revocation_reason text,created_by text not null,created_at timestamptz not null,primary key(tenant_id,attestation_id),
              foreign key(tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),foreign key(tenant_id,trust_evidence_id) references a2a_peer_trust_evidence(tenant_id,evidence_id))
            """);
        jdbc.execute("create unique index uq_c0b3_verified on a2a_peer_processing_attestations(tenant_id,peer_id) where attestation_status='VERIFIED'");
        jdbc.execute("""
            create or replace function protect_c0b3_test() returns trigger language plpgsql as $$
            begin
              if old.processing_region is distinct from new.processing_region or old.storage_region is distinct from new.storage_region or old.attestation_type is distinct from new.attestation_type then
                raise exception 'C0_B3_PROCESSING_ATTESTATION_FACT_IMMUTABLE' using errcode='55000';
              end if;
              return new;
            end $$
            """);
        jdbc.execute("create trigger trg_c0b3_test before update on a2a_peer_processing_attestations for each row execute function protect_c0b3_test()");
        jdbc.execute("""
            create table a2a_trust_assurance_policies(
              tenant_id text not null,policy_id text not null,display_name text not null,description text,policy_status text not null,policy_version bigint not null,
              created_by text not null,created_at timestamptz not null,activated_at timestamptz,retired_at timestamptz,updated_at timestamptz not null,primary key(tenant_id,policy_id))
            """);
        jdbc.execute("create unique index uq_c0b3_active_policy on a2a_trust_assurance_policies(tenant_id) where policy_status='ACTIVE'");
        jdbc.execute("""
            create table a2a_trust_assurance_rules(
              tenant_id text not null,policy_id text not null,rule_id text not null,grant_type text not null,subject_type text not null,
              required_evidence_sets_json jsonb not null,description text,created_at timestamptz not null,primary key(tenant_id,policy_id,rule_id))
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
        jdbc.update("insert into a2a_peer_registrations values('tenant-a','peer-a','Peer A','https://peer.example/card','INTEGRITY_RECORDED','DRAFT',now(),now())");
    }

    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
