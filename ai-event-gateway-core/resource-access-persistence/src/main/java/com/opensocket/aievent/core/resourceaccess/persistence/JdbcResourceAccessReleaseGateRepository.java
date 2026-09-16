package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.Array;import java.sql.Timestamp;import java.time.Instant;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL release evidence adapter. It stores evidence but never changes process environment or feature flags. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public class JdbcResourceAccessReleaseGateRepository implements ResourceAccessReleaseGateRepository {
 private final JdbcTemplate jdbc;
 public JdbcResourceAccessReleaseGateRepository(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}
 @Override public ShadowMismatchAggregate aggregateShadowEvidence(String tenantId,Instant from,Instant to){
  Map<String,Object> row=jdbc.queryForMap("""
   with decision as (
    select count(*) samples,
      count(*) filter(where mismatch_category not in ('MATCH_ALLOW','MATCH_DENY')) mismatches,
      count(*) filter(where mismatch_category in ('LEGACY_ALLOW_RESOURCE_DENY','LEGACY_DENY_RESOURCE_ALLOW','LEGACY_ERROR','RESOURCE_ERROR')) critical,
      count(*) filter(where mismatch_category='LEGACY_NOT_AVAILABLE') unavailable,
      count(*) filter(where mismatch_category in ('LEGACY_ERROR','RESOURCE_ERROR')) errors
    from resource_shadow_decision_comparisons where tenant_id=? and compared_at>=? and compared_at<?
   ), task_list as (
    select (select count(*) from resource_task_scope_query_audits where tenant_id=? and created_at>=? and created_at<?) samples,
      count(*)::bigint mismatches,
      count(*)::bigint critical,0::bigint unavailable,0::bigint errors
    from resource_task_scope_shadow_mismatches where tenant_id=? and created_at>=? and created_at<?
   ), integration_list as (
    select (select count(*) from resource_integration_scope_query_audits where tenant_id=? and created_at>=? and created_at<?) samples,
      count(*)::bigint mismatches,
      count(*)::bigint critical,0::bigint unavailable,0::bigint errors
    from resource_integration_scope_shadow_mismatches where tenant_id=? and created_at>=? and created_at<?
   ), critical_reason as (
    select count(*) critical,count(*) filter(where effect='ERROR') errors
    from resource_authorization_decisions
    where tenant_id=? and decision_mode='SHADOW' and evaluated_at>=? and evaluated_at<? and (
      effect='ERROR' or reason_codes && array['CROSS_TENANT_RESOURCE_ACCESS_DENIED','EXPLICIT_DENY_MATCHED','SENSITIVITY_CLEARANCE_INSUFFICIENT','RESOURCE_SECURITY_EPOCH_STALE','STALE_EPOCH_ALLOW_REJECTED']::varchar[]
    )
   )
   select decision.samples+task_list.samples+integration_list.samples samples,
          decision.mismatches+task_list.mismatches+integration_list.mismatches mismatches,
          decision.critical+critical_reason.critical critical,decision.unavailable unavailable,
          decision.errors+critical_reason.errors errors
   from decision,task_list,integration_list,critical_reason
   """,tenantId,Timestamp.from(from),Timestamp.from(to),
   tenantId,Timestamp.from(from),Timestamp.from(to),tenantId,Timestamp.from(from),Timestamp.from(to),
   tenantId,Timestamp.from(from),Timestamp.from(to),tenantId,Timestamp.from(from),Timestamp.from(to),
   tenantId,Timestamp.from(from),Timestamp.from(to));
  return new ShadowMismatchAggregate(number(row.get("samples")),number(row.get("mismatches")),number(row.get("critical")),number(row.get("unavailable")),number(row.get("errors")),from,to);
 }
 @Override public List<ResourceAccessCertificationEvidence> latestCertificationEvidence(String tenantId){return jdbc.query("""
  select distinct on(evidence_type) * from resource_access_release_evidence
  where tenant_id=? order by evidence_type,completed_at desc,evidence_id desc
  """,(rs,n)->new ResourceAccessCertificationEvidence(rs.getString("evidence_id"),tenantId,ResourceAccessCertificationType.valueOf(rs.getString("evidence_type")),ResourceAccessCertificationStatus.valueOf(rs.getString("evidence_status")),rs.getString("command_name"),rs.getString("artifact_ref"),rs.getString("artifact_sha256"),rs.getString("summary"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("completed_at").toInstant(),rs.getString("actor_id"),rs.getString("correlation_id")),tenantId);}
 @Override public List<LegacyBypassInventoryItem> activeLegacyBypasses(String tenantId,Instant at){return jdbc.query("""
  select * from resource_access_legacy_bypass_inventory where tenant_id=? and bypass_status='ACTIVE' and expires_at>? order by component,bypass_id
  """,(rs,n)->new LegacyBypassInventoryItem(rs.getString("bypass_id"),tenantId,rs.getString("component"),rs.getString("path_pattern"),rs.getString("owner_id"),rs.getString("rationale"),rs.getTimestamp("expires_at").toInstant(),true,rs.getString("replacement_ref"),rs.getLong("version")),tenantId,Timestamp.from(at));}
 @Override @Transactional public void appendAssessment(ResourceAccessReleaseAssessment a){jdbc.update(c-> {var ps=c.prepareStatement("""
  insert into resource_access_release_assessments(tenant_id,assessment_id,from_mode,target_mode,assessment_status,sample_count,mismatch_count,critical_mismatch_count,legacy_unavailable_count,mismatch_rate_bps,legacy_unavailable_rate_bps,blocking_reasons,window_started_at,window_ended_at,assessed_at,assessed_by,correlation_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
  """);int i=1;ps.setString(i++,a.tenantId());ps.setString(i++,a.assessmentId());ps.setString(i++,a.fromMode().name());ps.setString(i++,a.targetMode().name());ps.setString(i++,a.status().name());ps.setLong(i++,a.sampleCount());ps.setLong(i++,a.mismatchCount());ps.setLong(i++,a.criticalMismatchCount());ps.setLong(i++,a.legacyUnavailableCount());ps.setInt(i++,a.mismatchRateBps());ps.setInt(i++,a.legacyUnavailableRateBps());Array reasons=c.createArrayOf("varchar",a.blockingReasons().toArray());ps.setArray(i++,reasons);ps.setTimestamp(i++,Timestamp.from(a.windowStartedAt()));ps.setTimestamp(i++,Timestamp.from(a.windowEndedAt()));ps.setTimestamp(i++,Timestamp.from(a.assessedAt()));ps.setString(i++,a.assessedBy());ps.setString(i,a.correlationId());return ps;});}
 @Override public void appendRolloutTransition(String tenantId,String transitionId,ResourceAccessEnforcementMode fromMode,ResourceAccessEnforcementMode toMode,String assessmentId,String actorId,String reason,String correlationId,Instant at){jdbc.update("""
  insert into resource_access_rollout_transitions(tenant_id,transition_id,from_mode,to_mode,assessment_id,actor_id,reason,correlation_id,transitioned_at) values(?,?,?,?,?,?,?,?,?)
  """,tenantId,transitionId,fromMode.name(),toMode.name(),assessmentId,actorId,reason,correlationId,Timestamp.from(at));}
 @Override public void appendRollbackRehearsal(String tenantId,String rehearsalId,ResourceAccessEnforcementMode fromMode,ResourceAccessEnforcementMode toMode,boolean passed,String incidentId,String artifactRef,String actorId,String correlationId,Instant at){jdbc.update("""
  insert into resource_access_rollback_rehearsals(tenant_id,rehearsal_id,from_mode,to_mode,rehearsal_status,incident_id,artifact_ref,actor_id,correlation_id,rehearsed_at) values(?,?,?,?,?,?,?,?,?,?)
  """,tenantId,rehearsalId,fromMode.name(),toMode.name(),passed?"PASS":"FAIL",incidentId,artifactRef,actorId,correlationId,Timestamp.from(at));}
 private static long number(Object value){return value instanceof Number n?n.longValue():Long.parseLong(String.valueOf(value));}
}
