package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.*;import java.time.Instant;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL evidence adapter for the P4RA-D decision engine. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public class JdbcResourceDecisionEvidenceRepository implements ResourceDecisionEvidenceRepository {
 private final JdbcTemplate jdbc;
 public JdbcResourceDecisionEvidenceRepository(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}
 @Override public List<ScopeGrantRecord> findEffectiveScopeGrants(String tenantId,Set<PolicyPrincipalRef> principals,String permission,ResourceType type,Instant at){
  Query q=principalQuery("select * from resource_scope_grants where tenant_id=? and permission_code=? and resource_type=? and grant_state='ACTIVE' and valid_from<=? and (valid_to is null or valid_to>?) and (",tenantId,permission,type.name(),Timestamp.from(at),Timestamp.from(at),principals);
  return jdbc.query(q.sql()+") order by scope_grant_id",(rs,row)->grant(rs),q.args().toArray());
 }
 @Override public List<ExplicitDenyRecord> findEffectiveExplicitDenies(String tenantId,Set<PolicyPrincipalRef> principals,String permission,ResourceType type,Instant at){
  Query q=principalQuery("select * from resource_scope_denies where tenant_id=? and (permission_code is null or permission_code='' or permission_code=?) and resource_type=? and deny_state='ACTIVE' and valid_from<=? and (valid_to is null or valid_to>?) and (",tenantId,permission,type.name(),Timestamp.from(at),Timestamp.from(at),principals);
  return jdbc.query(q.sql()+") order by severity desc,scope_deny_id",(rs,row)->deny(rs),q.args().toArray());
 }
 @Override public List<ResourceParticipantProjection> findEffectiveParticipants(ResourceRef ref,Instant at){return jdbc.query("""
  select * from resource_participants where tenant_id=? and resource_type=? and resource_id=? and participant_status='ACTIVE' and valid_from<=? and (valid_to is null or valid_to>?) order by participant_id
  """,(rs,row)->participant(rs,ref),ref.tenantId(),ref.resourceType().name(),ref.resourceId(),Timestamp.from(at),Timestamp.from(at));}
 @Override public Optional<PrincipalClearanceRecord> findEffectiveClearance(String tenantId,PolicyPrincipalRef principal,Instant at){return jdbc.query("""
  select * from resource_principal_clearances where tenant_id=? and principal_type=? and principal_id=? and clearance_status='ACTIVE' and valid_from<=? and (valid_to is null or valid_to>?) order by clearance_level desc,version desc limit 1
  """,(rs,row)->clearance(rs),tenantId,principal.principalType().name(),principal.principalId(),Timestamp.from(at),Timestamp.from(at)).stream().findFirst();}
 @Override public Optional<VisibilityPolicyRecord> findActiveVisibilityPolicy(String tenantId,ResourceType type){return jdbc.query("select * from resource_visibility_policies where tenant_id=? and resource_type=? and policy_state='ACTIVE' limit 1",(rs,row)->policy(rs),tenantId,type.name()).stream().findFirst();}
 @Override public PrincipalScopeSnapshot resolvePrincipalScope(String tenantId,PrincipalRef principal,Instant at){
  if(principal.principalType()!=PrincipalRef.PrincipalType.USER)return new PrincipalScopeSnapshot(tenantId,Set.of(),Set.of(),departmentRevision(tenantId));
  Set<String> departments=new LinkedHashSet<>(jdbc.query("select department_id from org_department_memberships where tenant_id=? and user_id=? and status='ACTIVE' and effective_at<=? and (expires_at is null or expires_at>?)",(rs,row)->rs.getString(1),tenantId,principal.principalId(),Timestamp.from(at),Timestamp.from(at)));
  Set<String> groups=new LinkedHashSet<>(jdbc.query("select group_id from org_group_memberships where tenant_id=? and user_id=? and status='ACTIVE' and effective_at<=? and (expires_at is null or expires_at>?)",(rs,row)->rs.getString(1),tenantId,principal.principalId(),Timestamp.from(at),Timestamp.from(at)));
  return new PrincipalScopeSnapshot(tenantId,departments,groups,departmentRevision(tenantId));
 }
 @Override public boolean departmentContains(String tenantId,String ancestor,String descendant){if(ancestor==null||ancestor.isBlank()||descendant==null||descendant.isBlank())return false;Integer n=jdbc.queryForObject("select count(*) from org_department_closure where tenant_id=? and ancestor_department_id=? and descendant_department_id=?",Integer.class,tenantId,ancestor,descendant);return n!=null&&n>0;}
 @Override public PolicyVersion currentPolicyVersion(String tenantId){Map<String,Object> row=jdbc.queryForMap("""
  select coalesce((select max(catalog_version) from resource_catalog),1) catalog_version,
         coalesce((select policy_revision from resource_access_policy_revisions where tenant_id=? and status='ACTIVE' order by policy_revision desc limit 1),0) policy_revision,
         coalesce((select content_hash from resource_access_policy_revisions where tenant_id=? and status='ACTIVE' order by policy_revision desc limit 1),'') content_hash
  """,tenantId,tenantId);return new PolicyVersion(num(row.get("catalog_version")),num(row.get("policy_revision")),String.valueOf(row.get("content_hash")));}
 @Override public SecurityEpoch currentSecurityEpoch(String tenantId,PrincipalRef principal,ResourceRef ref){Map<String,Object> row=jdbc.queryForMap("""
  select coalesce((select security_epoch from iam_global_security_epoch where singleton_id='GLOBAL'),0) global_epoch,
         coalesce((select security_epoch from iam_tenant_security_epochs where tenant_id=?),0)+coalesce((select security_epoch from resource_access_tenant_security_epochs where tenant_id=?),0) tenant_epoch,
         coalesce((select security_epoch from iam_global_principal_security_epochs where principal_id=?),0)+coalesce((select security_epoch from iam_principal_security_epochs where tenant_id=? and principal_id=?),0)+coalesce((select security_epoch from resource_access_principal_security_epochs where tenant_id=? and principal_id=?),0) principal_epoch,
         coalesce((select resource_security_epoch from resource_security_epochs where tenant_id=? and resource_type=? and resource_id=?),0) resource_epoch,
         coalesce((select catalog_version from resource_access_policy_revisions where tenant_id=? and status='ACTIVE' order by policy_revision desc limit 1),0) policy_catalog_version,
         coalesce((select max(revision) from org_department_revisions where tenant_id=?),0) department_revision
  """,tenantId,tenantId,principal.principalId(),tenantId,principal.principalId(),tenantId,principal.principalId(),tenantId,ref.resourceType().name(),ref.resourceId(),tenantId,tenantId);return new SecurityEpoch(num(row.get("global_epoch")),num(row.get("tenant_epoch")),num(row.get("principal_epoch")),num(row.get("resource_epoch")),num(row.get("policy_catalog_version")),num(row.get("department_revision")));}
 @Override @Transactional public void appendDecision(AuthorizationDecisionAuditRecord record){AuthorizationRequest r=record.request();AuthorizationDecision d=record.decision();jdbc.update("""
  insert into resource_authorization_decisions(tenant_id,decision_id,principal_type,principal_id,permission_code,resource_type,resource_id,requested_visibility,granted_visibility,effect,decision_mode,shadow_only,matched_role_binding_ids,matched_scope_grant_ids,matched_participant_ids,matched_ownership_evidence,matched_deny_ids,matched_scope_sources,reason_codes,policy_catalog_version,policy_revision,policy_content_hash,global_security_epoch,tenant_security_epoch,principal_security_epoch,resource_security_epoch,department_tree_revision,descriptor_hash,request_channel,operation_phase,purpose,correlation_id,cacheable,cache_ttl_ms,evaluated_at)
  values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,decision_id) do nothing
  """,r.resourceRef().tenantId(),d.decisionId(),r.principal().principalType().name(),r.principal().principalId(),d.permissionCode(),d.resourceRef().resourceType().name(),d.resourceRef().resourceId(),r.requestedVisibility().name(),d.grantedVisibility().name(),d.effect().name(),d.mode().name(),d.shadowOnly(),array(d.matchedRoleBindingIds()),array(d.matchedScopeGrantIds()),array(d.matchedParticipantIds()),array(d.matchedOwnershipEvidence()),array(d.matchedDenyIds()),array(d.matchedScopeSources()),array(d.reasons().stream().map(DecisionReason::code).toList()),d.policyVersion().catalogVersion(),d.policyVersion().policyRevision(),d.policyVersion().contentHash(),d.securityEpoch().globalEpoch(),d.securityEpoch().tenantEpoch(),d.securityEpoch().principalEpoch(),d.securityEpoch().resourceEpoch(),d.securityEpoch().departmentTreeRevision(),record.descriptorHash(),r.requestChannel().name(),r.operationPhase().name(),r.purpose(),r.correlationId(),d.cacheable(),d.cacheTtl().toMillis(),Timestamp.from(d.evaluatedAt()));}
 @Override @Transactional public void appendShadowComparison(ShadowDecisionComparison c){jdbc.update("""
  insert into resource_shadow_decision_comparisons(tenant_id,comparison_id,resource_type,resource_id,permission_code,legacy_effect,legacy_reason_code,legacy_decision_id,resource_effect,resource_decision_id,mismatch_category,correlation_id,compared_at)
  values (?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,comparison_id) do nothing
  """,c.tenantId(),c.comparisonId(),c.resourceRef().resourceType().name(),c.resourceRef().resourceId(),c.permissionCode(),c.legacyDecision().effect().name(),c.legacyDecision().reasonCode(),blank(c.legacyDecision().decisionId()),c.resourceDecision().effect().name(),c.resourceDecision().decisionId(),c.category().name(),c.correlationId(),Timestamp.from(c.comparedAt()));}
 @Override @Transactional public void appendShadowComparisonV2(ShadowDecisionComparisonV2 c){
  String state=jdbc.queryForObject("""
   select phase5j_enqueue_shadow_observation(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
   """,String.class,
   c.tenantId(),c.comparisonId(),c.domainCode(),c.entryPointId(),c.resourceRef().resourceType().name(),c.resourceRef().resourceId(),c.permissionCode(),
   c.legacy().effect(),array(c.legacy().effectiveScopes()),c.legacy().visibility().name(),c.legacy().reasonCode(),c.legacy().contextComplete(),blank(c.legacy().errorCode()),blank(c.legacy().decisionId()),
   c.target().effect(),array(c.target().effectiveScopes()),c.target().visibility().name(),c.target().reasonCode(),c.target().contextComplete(),blank(c.target().errorCode()),blank(c.target().decisionId()),
   c.category().name(),c.severity(),c.correlationId(),Timestamp.from(c.comparedAt()));
  if(state==null||state.isBlank())throw new IllegalStateException("PHASE5J_SHADOW_PIPELINE_ENQUEUE_FAILED");
 }
 private long departmentRevision(String tenantId){Long v=jdbc.queryForObject("select coalesce(max(revision),0) from org_department_revisions where tenant_id=?",Long.class,tenantId);return v==null?0:v;}
 private Query principalQuery(String prefix,Object a,Object b,Object c,Object d,Object e,Set<PolicyPrincipalRef> principals){List<Object>args=new ArrayList<>(List.of(a,b,c,d,e));StringBuilder sql=new StringBuilder(prefix);if(principals==null||principals.isEmpty()){sql.append("1=0");return new Query(sql.toString(),args);}int i=0;for(PolicyPrincipalRef p:principals){if(i++>0)sql.append(" or ");sql.append("(principal_type=? and principal_id=?)");args.add(p.principalType().name());args.add(p.principalId());}return new Query(sql.toString(),args);}
 private ScopeGrantRecord grant(ResultSet rs)throws SQLException{return new ScopeGrantRecord(rs.getString("tenant_id"),rs.getString("scope_grant_id"),ScopePrincipalType.valueOf(rs.getString("principal_type")),rs.getString("principal_id"),rs.getString("permission_code"),ResourceType.valueOf(rs.getString("resource_type")),ScopeType.valueOf(rs.getString("scope_type")),nullBlank(rs.getString("scope_ref_id")),VisibilityLevel.valueOf(rs.getString("visibility_level")),instant(rs,"valid_from"),nullable(rs,"valid_to"),ScopeGrantSource.valueOf(rs.getString("grant_source")),rs.getString("grant_reason"),rs.getString("created_by"),nullBlank(rs.getString("approved_by")),ScopeGrantState.valueOf(rs.getString("grant_state")),rs.getString("idempotency_key"),rs.getLong("version"),instant(rs,"created_at"),instant(rs,"updated_at"));}
 private ExplicitDenyRecord deny(ResultSet rs)throws SQLException{return new ExplicitDenyRecord(rs.getString("tenant_id"),rs.getString("scope_deny_id"),ScopePrincipalType.valueOf(rs.getString("principal_type")),rs.getString("principal_id"),nullBlank(rs.getString("permission_code")),ResourceType.valueOf(rs.getString("resource_type")),ScopeType.valueOf(rs.getString("scope_type")),nullBlank(rs.getString("scope_ref_id")),rs.getString("deny_reason"),DenySeverity.valueOf(rs.getString("severity")),instant(rs,"valid_from"),nullable(rs,"valid_to"),rs.getString("created_by"),nullBlank(rs.getString("approved_by")),ScopeDenyState.valueOf(rs.getString("deny_state")),rs.getString("idempotency_key"),rs.getLong("version"),instant(rs,"created_at"),instant(rs,"updated_at"));}
 private ResourceParticipantProjection participant(ResultSet rs,ResourceRef ref)throws SQLException{return new ResourceParticipantProjection(rs.getString("participant_id"),ref,ResourceParticipantType.valueOf(rs.getString("participant_type")),rs.getString("participant_ref_id"),ResourceParticipantRole.valueOf(rs.getString("participant_role")),VisibilityLevel.valueOf(rs.getString("visibility_level")),strings(rs.getArray("allowed_permission_codes")),instant(rs,"valid_from"),nullable(rs,"valid_to"),DescriptorAuthority.valueOf(rs.getString("source_authority")),rs.getLong("source_version"),ResourceParticipantStatus.valueOf(rs.getString("participant_status")));}
 private PrincipalClearanceRecord clearance(ResultSet rs)throws SQLException{return new PrincipalClearanceRecord(rs.getString("tenant_id"),rs.getString("clearance_id"),ScopePrincipalType.valueOf(rs.getString("principal_type")),rs.getString("principal_id"),SensitivityLevel.valueOf(rs.getString("clearance_level")),instant(rs,"valid_from"),nullable(rs,"valid_to"),ClearanceStatus.valueOf(rs.getString("clearance_status")),nullBlank(rs.getString("approved_by")),rs.getString("reason"),rs.getLong("version"),instant(rs,"created_at"),instant(rs,"updated_at"));}
 private VisibilityPolicyRecord policy(ResultSet rs)throws SQLException{String tenant=rs.getString("tenant_id"),id=rs.getString("visibility_policy_id");List<VisibilityFieldRule>rules=jdbc.query("select * from resource_visibility_fields where tenant_id=? and visibility_policy_id=? order by priority,field_path",(r,row)->new VisibilityFieldRule(r.getString("field_rule_id"),r.getString("field_path"),VisibilityLevel.valueOf(r.getString("minimum_visibility_level")),SensitivityLevel.valueOf(r.getString("sensitivity_level")),MaskingMethod.valueOf(r.getString("masking_method")),r.getBoolean("export_allowed"),r.getBoolean("download_allowed"),r.getInt("priority"),r.getLong("version")),tenant,id);return new VisibilityPolicyRecord(tenant,id,ResourceType.valueOf(rs.getString("resource_type")),rs.getString("policy_name"),VisibilityLevel.valueOf(rs.getString("maximum_visibility")),SensitivityLevel.valueOf(rs.getString("maximum_sensitivity")),VisibilityPolicyState.valueOf(rs.getString("policy_state")),rules,rs.getLong("version"),rs.getString("created_by"),rs.getString("updated_by"),instant(rs,"created_at"),instant(rs,"updated_at"));}
 private static List<String> strings(Array a)throws SQLException{if(a==null)return List.of();Object raw=a.getArray();if(raw instanceof String[]v)return List.of(v);Object[]v=(Object[])raw;List<String>x=new ArrayList<>();for(Object o:v)if(o!=null)x.add(String.valueOf(o));return List.copyOf(x);}
 private static String array(Collection<String>v){return String.join(",",v);}
 private static long num(Object v){return v==null?0:((Number)v).longValue();}
 private static Instant instant(ResultSet rs,String c)throws SQLException{return rs.getTimestamp(c).toInstant();}
 private static Instant nullable(ResultSet rs,String c)throws SQLException{Timestamp t=rs.getTimestamp(c);return t==null?null:t.toInstant();}
 private static String nullBlank(String v){return v==null?"":v;}
 private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
 private record Query(String sql,List<Object>args){}
}
