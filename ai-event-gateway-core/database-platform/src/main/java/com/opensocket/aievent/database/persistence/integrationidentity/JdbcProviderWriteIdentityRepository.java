package com.opensocket.aievent.database.persistence.integrationidentity;

import com.opensocket.aievent.core.issuetracking.identity.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persists verified actor bindings and immutable dual-actor provider execution attribution. Never stores provider secrets. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="integration-identity",name="store",havingValue="MYBATIS")
public class JdbcProviderWriteIdentityRepository implements ExternalActorBindingRepository,ProviderExecutionAttributionRepository {
 private final JdbcTemplate jdbc;
 public JdbcProviderWriteIdentityRepository(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc,"jdbc");}
 @Override public Optional<ExternalActorBindingRef> findVerified(String tenant,String human,String connection){return jdbc.query("""
  select * from integration_external_actor_bindings where tenant_id=? and human_principal_id=? and connection_id=? and binding_status='VERIFIED' and (expires_at is null or expires_at>now()) order by verified_at desc limit 1
  """,this::binding,tenant,human,connection).stream().findFirst();}
 @Override @Transactional public ExternalActorBindingRef save(ExternalActorBindingRef v){jdbc.update("""
  insert into integration_external_actor_bindings(tenant_id,binding_id,human_principal_id,connection_id,provider_actor_id,integration_principal_id,credential_id,binding_status,verified_at,expires_at,version,created_at,updated_at)
  values(?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,binding_id) do update set provider_actor_id=excluded.provider_actor_id,integration_principal_id=excluded.integration_principal_id,credential_id=excluded.credential_id,binding_status=excluded.binding_status,verified_at=excluded.verified_at,expires_at=excluded.expires_at,version=excluded.version,updated_at=excluded.updated_at
  """,v.tenantId(),v.bindingId(),v.humanPrincipalId(),v.connectionId(),v.providerActorId(),v.integrationPrincipalId(),v.credentialId(),v.status().name(),v.verifiedAt(),v.expiresAt(),v.version(),java.time.OffsetDateTime.now(),java.time.OffsetDateTime.now());return v;}
 @Override @Transactional public ProviderExecutionAttribution append(ProviderExecutionAttribution v){jdbc.update("""
  insert into integration_provider_execution_attributions(tenant_id,attribution_id,human_principal_id,authorization_decision_id,resource_type,resource_id,resource_action,purpose,connection_id,mapping_id,integration_principal_id,credential_id,credential_version,provider_actor_id,write_identity_policy,attribution_status,correlation_id,idempotency_key,provider_operation_id,failure_code,created_at)
  values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
  """,v.tenantId(),v.attributionId(),v.humanPrincipalId(),v.authorizationDecisionId(),v.resourceType(),v.resourceId(),v.resourceAction(),v.purpose(),v.connectionId(),v.mappingId(),v.integrationPrincipalId(),v.credentialId(),v.credentialVersion(),blank(v.providerActorId()),v.writeIdentityPolicy().name(),v.status().name(),v.correlationId(),v.idempotencyKey(),blank(v.providerOperationId()),blank(v.failureCode()),v.createdAt());return v;}
 @Override public Optional<ProviderExecutionAttribution> findByIdempotencyKey(String tenant,String key){return jdbc.query("select * from integration_provider_execution_attributions where tenant_id=? and idempotency_key=?",this::attribution,tenant,key).stream().findFirst();}
 private ExternalActorBindingRef binding(ResultSet r,int n)throws SQLException{return new ExternalActorBindingRef(r.getString("tenant_id"),r.getString("binding_id"),r.getString("human_principal_id"),r.getString("connection_id"),r.getString("provider_actor_id"),r.getString("integration_principal_id"),r.getString("credential_id"),ExternalActorBindingStatus.valueOf(r.getString("binding_status")),r.getObject("verified_at",java.time.OffsetDateTime.class),r.getObject("expires_at",java.time.OffsetDateTime.class),r.getLong("version"));}
 private ProviderExecutionAttribution attribution(ResultSet r,int n)throws SQLException{return new ProviderExecutionAttribution(r.getString("tenant_id"),r.getString("attribution_id"),r.getString("human_principal_id"),r.getString("authorization_decision_id"),r.getString("resource_type"),r.getString("resource_id"),r.getString("resource_action"),r.getString("purpose"),r.getString("connection_id"),r.getString("mapping_id"),r.getString("integration_principal_id"),r.getString("credential_id"),r.getString("credential_version"),blank(r.getString("provider_actor_id")),ProviderWriteIdentityPolicy.valueOf(r.getString("write_identity_policy")),ProviderExecutionAttributionStatus.valueOf(r.getString("attribution_status")),r.getString("correlation_id"),r.getString("idempotency_key"),blank(r.getString("provider_operation_id")),blank(r.getString("failure_code")),r.getObject("created_at",java.time.OffsetDateTime.class));}
 private static String blank(String v){return v==null?"":v;}
}
