package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/** PC-S5 real-PostgreSQL proof that endpoint, processing and storage facts are all required and UNKNOWN fails closed. */
@Tag("container") @Tag("production-closure")
@Testcontainers(disabledWithoutDocker = true)
class PCStage5ResidencyAuthorizationContainerTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_pc_s5").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc; private A2AResidencyAuthorizationService residency;

    @BeforeEach void setUp(){DataSource ds=dataSource();jdbc=new JdbcTemplate(ds);residency=new A2AResidencyAuthorizationService(new NamedParameterJdbcTemplate(ds),new ObjectMapper());
        jdbc.execute("drop table if exists a2a_residency_authorization_decisions,a2a_peer_processing_attestations,a2a_residency_policies,a2a_peer_interfaces cascade");
        jdbc.execute("create table a2a_peer_interfaces(tenant_id text,interface_id text,peer_id text,endpoint_region text)");
        jdbc.execute("create table a2a_residency_policies(tenant_id text,policy_id text,policy_version bigint,policy_status text,emergency_kill_switch boolean,allowed_endpoint_regions_json jsonb,allowed_processing_regions_json jsonb,allowed_storage_regions_json jsonb)");
        jdbc.execute("create table a2a_peer_processing_attestations(tenant_id text,attestation_id text,peer_id text,processing_region text,storage_region text,attestation_status text,valid_from timestamptz,valid_until timestamptz,verified_at timestamptz)");
        jdbc.execute("create table a2a_residency_authorization_decisions(tenant_id text,decision_id text,peer_id text,interface_id text,policy_id text,policy_version bigint,processing_attestation_id text,endpoint_region text,processing_region text,storage_region text,decision text,reason_codes_json jsonb,decided_at timestamptz)");
        jdbc.execute("insert into a2a_peer_interfaces values('tenant-a','if-a','peer-a','TW')");
    }

    @Test void allThreeRegionFactsMustBeAllowed(){policy(false);attestation("TW","TW");var d=residency.authorizeAdmission("tenant-a","if-a");assertThat(d.allowed()).isTrue();assertThat(d.endpointRegion()).isEqualTo("TW");assertThat(d.processingRegion()).isEqualTo("TW");assertThat(d.storageRegion()).isEqualTo("TW");assertThat(jdbc.queryForObject("select count(*) from a2a_residency_authorization_decisions where decision='ALLOWED'",Integer.class)).isEqualTo(1);}
    @Test void missingProcessingAttestationFailsClosedAsUnknown(){policy(false);IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->residency.authorizeAdmission("tenant-a","if-a"));assertThat(ex.getMessage()).startsWith("A2A_RESIDENCY_UNKNOWN:");}
    @Test void emergencyKillSwitchDeniesEvenOtherwiseAllowedRegions(){policy(true);attestation("TW","TW");IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->residency.authorizeAdmission("tenant-a","if-a"));assertThat(ex.getMessage()).contains("A2A_RESIDENCY_KILL_SWITCH");}
    @Test void processingOrStorageRegionOutsidePolicyIsDenied(){policy(false);attestation("US","TW");IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->residency.authorizeAdmission("tenant-a","if-a"));assertThat(ex.getMessage()).contains("A2A_PROCESSING_REGION_DENIED");}

    private void policy(boolean kill){jdbc.update("insert into a2a_residency_policies values('tenant-a','res-policy',1,'ACTIVE',?, '[\"TW\"]','[\"TW\"]','[\"TW\"]')",kill);}
    private void attestation(String processing,String storage){jdbc.update("insert into a2a_peer_processing_attestations values('tenant-a','att-a','peer-a',?,?,'VERIFIED',now()-interval '1 minute',now()+interval '1 hour',now())",processing,storage);}
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
