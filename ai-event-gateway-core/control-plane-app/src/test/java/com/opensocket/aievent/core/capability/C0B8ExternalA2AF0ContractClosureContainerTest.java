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

/** PostgreSQL proof for C0-B8 credential/policy authority and immutable execution security snapshots. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class C0B8ExternalA2AF0ContractClosureContainerTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_c0b8").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc; private A2AExternalF0SecurityService security;

    @BeforeEach void setUp(){DataSource ds=dataSource();jdbc=new JdbcTemplate(ds);security=new A2AExternalF0SecurityService(new NamedParameterJdbcTemplate(ds),new ObjectMapper(),new A2APeerCredentialSecretResolver());recreate();}

    @Test void interfaceNotEligibleWithoutValidatedCredentialBinding(){
        assertThat(jdbc.queryForObject("select a2a_interface_current_contract_eligible('tenant-a','if-a')",Boolean.class)).isFalse();
        jdbc.update("insert into a2a_interface_credential_bindings(tenant_id,binding_id,interface_id,security_scheme_ref,credential_type,secret_ref,binding_status,validation_status) values('tenant-a','cred-a','if-a','bearer','BEARER_TOKEN','env://PATH','ACTIVE','VALID')");
        assertThat(jdbc.queryForObject("select a2a_interface_current_contract_eligible('tenant-a','if-a')",Boolean.class)).isTrue();
    }

    @Test void mtlsCannotBecomeRuntimeActive(){
        security.upsertCredentialDraft("tenant-a","if-a","cred-mtls","mtls","MTLS","env://PATH",null,null);
        assertThat(security.validateCredential("tenant-a","cred-mtls").get("validation_status")).isEqualTo("INVALID");
        assertThrows(IllegalArgumentException.class,()->security.activateCredential("tenant-a","cred-mtls"));
    }

    @Test void outboundPolicyMustBeRuntimeReady(){
        security.upsertOutboundPolicyDraft("tenant-a","pin-policy","Pinned","not yet supported",List.of("https"),List.of(),List.of(),true,true,true,true,0,true,false,5000,10000,"TLSv1.3",List.of("sha256/example"));
        assertThrows(IllegalArgumentException.class,()->security.activateOutboundPolicy("tenant-a","pin-policy"));
    }

    @Test void newExecutionSnapshotsCredentialAndOutboundPolicy(){
        jdbc.update("insert into a2a_interface_credential_bindings(tenant_id,binding_id,interface_id,security_scheme_ref,credential_type,secret_ref,binding_status,validation_status) values('tenant-a','cred-a','if-a','bearer','BEARER_TOKEN','env://PATH','ACTIVE','VALID')");
        A2AExternalF0SecurityService.SecuritySnapshot snap=security.snapshotForInterface("tenant-a","if-a");
        jdbc.update("insert into a2a_remote_read_executions(tenant_id,execution_id,credential_binding_id,outbound_destination_policy_id) values('tenant-a','exec-a',?,?)",snap.credentialBindingId(),snap.outboundDestinationPolicyId());
        assertThat(jdbc.queryForMap("select credential_binding_id,outbound_destination_policy_id from a2a_remote_read_executions where execution_id='exec-a'")).containsEntry("credential_binding_id","cred-a").containsEntry("outbound_destination_policy_id","EXTERNAL_HTTP_DEFAULT");
    }

    @Test void runtimeSecurityReusesExecutionSnapshotsAfterRotation(){
        jdbc.update("insert into a2a_interface_credential_bindings(tenant_id,binding_id,interface_id,security_scheme_ref,credential_type,secret_ref,binding_status,validation_status) values('tenant-a','cred-old','if-a','bearer','BEARER_TOKEN','env://PATH','RETIRED','VALID')");
        jdbc.update("insert into a2a_interface_credential_bindings(tenant_id,binding_id,interface_id,security_scheme_ref,credential_type,secret_ref,binding_status,validation_status) values('tenant-a','cred-new','if-a','bearer','BEARER_TOKEN','env://PATH','ACTIVE','VALID')");
        jdbc.update("insert into a2a_outbound_destination_policies(tenant_id,policy_id,display_name,allowed_schemes_json,allowlist_host_suffixes_json,allowlist_cidrs_json,policy_status,runtime_status) values('tenant-a','policy-old','Old','[\"https\"]','[]','[]','RETIRED','RUNTIME_READY')");
        jdbc.update("insert into a2a_remote_read_executions(tenant_id,execution_id,credential_binding_id,outbound_destination_policy_id) values('tenant-a','exec-old','cred-old','policy-old')");
        A2AExternalF0SecurityService.RuntimeSecurity runtime=security.runtimeSecurityForExecution("tenant-a","exec-old");
        assertThat(runtime.credentialBindingId()).isEqualTo("cred-old");
        assertThat(runtime.outboundDestinationPolicyId()).isEqualTo("policy-old");
    }

    private void recreate(){
        jdbc.execute("drop table if exists a2a_remote_read_executions,a2a_peer_provider_links,a2a_interface_credential_bindings,a2a_outbound_destination_policies,a2a_peer_interfaces cascade");
        jdbc.execute("create table a2a_peer_interfaces(tenant_id text not null,interface_id text not null,outbound_destination_policy_ref text,primary key(tenant_id,interface_id))");
        jdbc.execute("create table a2a_peer_provider_links(tenant_id text,provider_id text,interface_id text,status text,updated_at timestamptz,primary key(tenant_id,provider_id))");
        jdbc.execute("create table a2a_interface_credential_bindings(tenant_id text not null,binding_id text not null,interface_id text not null,security_scheme_ref text not null,credential_type text not null,secret_ref text not null,header_name text,auth_scheme text,binding_status text not null default 'DRAFT',validation_status text not null default 'UNKNOWN',validation_message text,validated_at timestamptz,valid_until timestamptz,binding_version bigint not null default 1,created_by text,activated_at timestamptz,retired_at timestamptz,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,binding_id))");
        jdbc.execute("create unique index uq_c0b8_active_cred on a2a_interface_credential_bindings(tenant_id,interface_id) where binding_status='ACTIVE'");
        jdbc.execute("create table a2a_outbound_destination_policies(tenant_id text not null,policy_id text not null,display_name text not null,description text,allowed_schemes_json jsonb not null default '[\"https\"]',allowlist_host_suffixes_json jsonb not null default '[]',allowlist_cidrs_json jsonb not null default '[]',deny_private_addresses boolean not null default true,deny_loopback_addresses boolean not null default true,deny_link_local_addresses boolean not null default true,deny_cloud_metadata_endpoints boolean not null default true,max_redirects int not null default 0,follow_redirect_same_origin_only boolean not null default true,dns_rebinding_protection boolean not null default false,connect_timeout_ms int not null default 10000,read_timeout_ms int not null default 45000,required_tls_version text,certificate_pins_json jsonb not null default '[]',policy_status text not null default 'DRAFT',runtime_status text not null default 'RUNTIME_READY',runtime_status_reason text,policy_version bigint not null default 1,created_by text,activated_at timestamptz,retired_at timestamptz,created_at timestamptz not null default now(),updated_at timestamptz not null default now(),primary key(tenant_id,policy_id))");
        jdbc.execute("create table a2a_remote_read_executions(tenant_id text not null,execution_id text not null,credential_binding_id text,outbound_destination_policy_id text,primary key(tenant_id,execution_id))");
        jdbc.execute("create or replace function a2a_interface_current_credential_binding_id(p_tenant text,p_interface text) returns text language sql stable as $$ select binding_id from a2a_interface_credential_bindings where tenant_id=p_tenant and interface_id=p_interface and binding_status='ACTIVE' and validation_status='VALID' and (valid_until is null or valid_until>now()) order by binding_version desc limit 1 $$");
        jdbc.execute("create or replace function a2a_interface_current_outbound_policy_id(p_tenant text,p_interface text) returns text language sql stable as $$ select p.policy_id from a2a_peer_interfaces i join a2a_outbound_destination_policies p on p.tenant_id=i.tenant_id and p.policy_id=i.outbound_destination_policy_ref where i.tenant_id=p_tenant and i.interface_id=p_interface and p.policy_status='ACTIVE' and p.runtime_status='RUNTIME_READY' limit 1 $$");
        jdbc.execute("create or replace function a2a_interface_current_contract_eligible(p_tenant text,p_interface text) returns boolean language sql stable as $$ select a2a_interface_current_credential_binding_id(p_tenant,p_interface) is not null and a2a_interface_current_outbound_policy_id(p_tenant,p_interface) is not null $$");
        jdbc.update("insert into a2a_peer_interfaces values('tenant-a','if-a','EXTERNAL_HTTP_DEFAULT')");
        jdbc.update("insert into a2a_outbound_destination_policies(tenant_id,policy_id,display_name,allowed_schemes_json,allowlist_host_suffixes_json,allowlist_cidrs_json,policy_status,runtime_status) values('tenant-a','EXTERNAL_HTTP_DEFAULT','Default','[\"http\",\"https\"]','[]','[]','ACTIVE','RUNTIME_READY')");
    }
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
