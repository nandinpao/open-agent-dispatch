package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.IntegrationScopeQueryAuditPort;
import com.opensocket.aievent.core.resourceaccess.contract.IntegrationScopeQueryPlan;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Append-only P4RA-F evidence for Issue/Integration query plans and SHADOW differences. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name={"enabled","integration-enabled"},havingValue="true")
public class JdbcIntegrationScopeQueryAuditAdapter implements IntegrationScopeQueryAuditPort {
 private final JdbcTemplate jdbc;
 public JdbcIntegrationScopeQueryAuditAdapter(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc,"jdbc");}
 @Override @Transactional public void recordPlan(IntegrationScopeQueryPlan p,String purpose,Instant at){jdbc.update("""
  insert into resource_integration_scope_query_audits(tenant_id,scope_query_audit_id,principal_type,principal_id,permission_code,resource_type,purpose,strategy,plan_hash,maximum_visibility,exact_department_count,subtree_root_count,group_count,explicit_resource_count,excluded_resource_count,policy_catalog_version,policy_revision,global_security_epoch,tenant_security_epoch,principal_security_epoch,resource_security_epoch,department_tree_revision,created_at)
  values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,plan_hash,purpose) do nothing
  """,p.tenantId(),UUID.randomUUID().toString(),p.principalType(),p.principalId(),p.permissionCode(),p.resourceType().name(),req(purpose),p.strategy().name(),p.planHash(),p.maximumVisibility().name(),p.exactDepartmentIds().size(),p.subtreeDepartmentRootIds().size(),p.groupIds().size(),p.explicitResourceIds().size(),p.excludedResourceIds().size(),p.policyVersion().catalogVersion(),p.policyVersion().policyRevision(),p.securityEpoch().globalEpoch(),p.securityEpoch().tenantEpoch(),p.securityEpoch().principalEpoch(),p.securityEpoch().resourceEpoch(),p.securityEpoch().departmentTreeRevision(),Timestamp.from(at));}
 @Override @Transactional public void recordShadowMismatch(IntegrationScopeQueryPlan p,String purpose,Set<String> legacyOnly,Set<String> scopedOnly,Instant at){jdbc.update("""
  insert into resource_integration_scope_shadow_mismatches(tenant_id,mismatch_id,principal_type,principal_id,permission_code,resource_type,purpose,plan_hash,legacy_only_count,scoped_only_count,legacy_only_sample,scoped_only_sample,created_at)
  values(?,?,?,?,?,?,?,?,?,?,?,?,?)
  """,p.tenantId(),UUID.randomUUID().toString(),p.principalType(),p.principalId(),p.permissionCode(),p.resourceType().name(),req(purpose),p.planHash(),legacyOnly.size(),scopedOnly.size(),sample(legacyOnly),sample(scopedOnly),Timestamp.from(at));}
 private static String sample(Set<String> v){return String.join(",",new TreeSet<>(v).stream().limit(50).toList());}
 private static String req(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("purpose is required");return v.trim();}
}
