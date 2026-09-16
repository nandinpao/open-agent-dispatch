package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.ResourcePolicyRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL policy governance adapter. No method in this adapter returns an authorization decision. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public class JdbcResourcePolicyRepository implements ResourcePolicyRepository {
    private final JdbcTemplate jdbc;
    public JdbcResourcePolicyRepository(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}

    @Override public Optional<ScopeGrantRecord> findScopeGrant(String tenantId,String grantId){return oneGrant("tenant_id=? and scope_grant_id=?",tenantId,grantId);}
    @Override public Optional<ScopeGrantRecord> findScopeGrantByIdempotencyKey(String tenantId,String key){return oneGrant("tenant_id=? and idempotency_key=?",tenantId,key);}
    private Optional<ScopeGrantRecord> oneGrant(String predicate,Object...args){return jdbc.query("select * from resource_scope_grants where "+predicate+" limit 1",(rs,row)->grant(rs),args).stream().findFirst();}

    @Override @Transactional public ScopeGrantRecord insertScopeGrant(ScopeGrantRecord g,String correlationId){
        jdbc.update("""
            insert into resource_scope_grants(tenant_id,scope_grant_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,visibility_level,valid_from,valid_to,grant_source,grant_reason,approved_by,grant_state,idempotency_key,version,created_at,created_by,updated_at,updated_by)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,g.tenantId(),g.grantId(),g.principalType().name(),g.principalId(),g.permissionCode(),g.resourceType().name(),g.scopeType().name(),blankToNull(g.scopeRefId()),g.visibilityLevel().name(),ts(g.validFrom()),ts(g.validTo()),g.grantSource().name(),g.grantReason(),blankToNull(g.approvedBy()),g.state().name(),g.idempotencyKey(),g.version(),ts(g.createdAt()),g.createdBy(),ts(g.updatedAt()),g.createdBy());
        insertGrantAudit(g.tenantId(),g.grantId(),null,g.state(),g.createdBy(),g.grantReason(),correlationId,g.idempotencyKey(),g.version(),g.createdAt());
        return g;
    }

    @Override @Transactional public ScopeGrantRecord transitionScopeGrant(ScopeGrantRecord current,ScopeGrantState target,String approvedBy,String actor,String reason,String correlation,String idempotency,Instant at){
        int n=jdbc.update("""
            update resource_scope_grants set grant_state=?,approved_by=?,version=version+1,updated_at=?,updated_by=?
             where tenant_id=? and scope_grant_id=? and version=? and grant_state=?
            """,target.name(),blankToNull(approvedBy),ts(at),actor,current.tenantId(),current.grantId(),current.version(),current.state().name());
        if(n!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        ScopeGrantRecord updated=findScopeGrant(current.tenantId(),current.grantId()).orElseThrow();
        insertGrantAudit(current.tenantId(),current.grantId(),current.state(),target,actor,reason,correlation,idempotency,updated.version(),at);
        return updated;
    }
    private void insertGrantAudit(String tenant,String grantId,ScopeGrantState previous,ScopeGrantState target,String actor,String reason,String correlation,String idem,long version,Instant at){
        jdbc.update("""
            insert into resource_scope_grant_audits(tenant_id,audit_id,scope_grant_id,previous_state,resulting_state,actor_id,reason,correlation_id,idempotency_key,resulting_version,occurred_at)
            values (?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
            """,tenant,id("grant-audit",tenant,idem),grantId,previous==null?null:previous.name(),target.name(),actor,reason,correlation,idem,version,ts(at));
    }

    @Override public Optional<ExplicitDenyRecord> findExplicitDeny(String tenantId,String denyId){return oneDeny("tenant_id=? and scope_deny_id=?",tenantId,denyId);}
    @Override public Optional<ExplicitDenyRecord> findExplicitDenyByIdempotencyKey(String tenantId,String key){return oneDeny("tenant_id=? and idempotency_key=?",tenantId,key);}
    private Optional<ExplicitDenyRecord> oneDeny(String predicate,Object...args){return jdbc.query("select * from resource_scope_denies where "+predicate+" limit 1",(rs,row)->deny(rs),args).stream().findFirst();}

    @Override @Transactional public ExplicitDenyRecord insertExplicitDeny(ExplicitDenyRecord d,String correlation){
        jdbc.update("""
            insert into resource_scope_denies(tenant_id,scope_deny_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,deny_reason,severity,valid_from,valid_to,created_by,approved_by,deny_state,idempotency_key,version,created_at,updated_at,updated_by)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,d.tenantId(),d.denyId(),d.principalType().name(),d.principalId(),blankToNull(d.permissionCode()),d.resourceType().name(),d.scopeType().name(),blankToNull(d.scopeRefId()),d.denyReason(),d.severity().name(),ts(d.validFrom()),ts(d.validTo()),d.createdBy(),blankToNull(d.approvedBy()),d.state().name(),d.idempotencyKey(),d.version(),ts(d.createdAt()),ts(d.updatedAt()),d.createdBy());
        insertDenyAudit(d.tenantId(),d.denyId(),null,d.state(),d.createdBy(),d.denyReason(),correlation,d.idempotencyKey(),d.version(),d.createdAt());
        return d;
    }

    @Override @Transactional public ExplicitDenyRecord transitionExplicitDeny(ExplicitDenyRecord current,ScopeDenyState target,String approvedBy,String actor,String reason,String correlation,String idempotency,Instant at){
        int n=jdbc.update("""
            update resource_scope_denies set deny_state=?,approved_by=?,version=version+1,updated_at=?,updated_by=?
             where tenant_id=? and scope_deny_id=? and version=? and deny_state=?
            """,target.name(),blankToNull(approvedBy),ts(at),actor,current.tenantId(),current.denyId(),current.version(),current.state().name());
        if(n!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        ExplicitDenyRecord updated=findExplicitDeny(current.tenantId(),current.denyId()).orElseThrow();
        insertDenyAudit(current.tenantId(),current.denyId(),current.state(),target,actor,reason,correlation,idempotency,updated.version(),at);
        return updated;
    }
    private void insertDenyAudit(String tenant,String denyId,ScopeDenyState previous,ScopeDenyState target,String actor,String reason,String correlation,String idem,long version,Instant at){
        jdbc.update("""
            insert into resource_scope_deny_audits(tenant_id,audit_id,scope_deny_id,previous_state,resulting_state,actor_id,reason,correlation_id,idempotency_key,resulting_version,occurred_at)
            values (?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
            """,tenant,id("deny-audit",tenant,idem),denyId,previous==null?null:previous.name(),target.name(),actor,reason,correlation,idem,version,ts(at));
    }

    @Override public Optional<VisibilityPolicyRecord> findVisibilityPolicy(String tenantId,String policyId){return onePolicy("tenant_id=? and visibility_policy_id=?",tenantId,policyId);}
    @Override public Optional<VisibilityPolicyRecord> findVisibilityPolicyByIdempotencyKey(String tenantId,String key){return onePolicy("tenant_id=? and idempotency_key=?",tenantId,key);}
    private Optional<VisibilityPolicyRecord> onePolicy(String predicate,Object...args){return jdbc.query("select * from resource_visibility_policies where "+predicate+" limit 1",(rs,row)->policy(rs),args).stream().findFirst();}

    @Override @Transactional public VisibilityPolicyRecord insertVisibilityPolicy(VisibilityPolicyRecord p,String idempotency,String correlation){
        jdbc.update("""
            insert into resource_visibility_policies(tenant_id,visibility_policy_id,resource_type,policy_name,maximum_visibility,maximum_sensitivity,policy_state,idempotency_key,version,created_at,created_by,updated_at,updated_by)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,p.tenantId(),p.policyId(),p.resourceType().name(),p.policyName(),p.maximumVisibility().name(),p.maximumSensitivity().name(),p.state().name(),idempotency,p.version(),ts(p.createdAt()),p.createdBy(),ts(p.updatedAt()),p.updatedBy());
        replaceFieldRules(p);
        insertVisibilityAudit(p.tenantId(),p.policyId(),null,p.state(),p.createdBy(),"VISIBILITY_POLICY_CREATED",correlation,idempotency,p.version(),p.createdAt());
        return p;
    }
    @Override @Transactional public VisibilityPolicyRecord transitionVisibilityPolicy(VisibilityPolicyRecord current,VisibilityPolicyState target,String actor,String reason,String correlation,String idempotency,Instant at){
        int n=jdbc.update("""
            update resource_visibility_policies set policy_state=?,version=version+1,updated_at=?,updated_by=?
             where tenant_id=? and visibility_policy_id=? and version=? and policy_state=?
            """,target.name(),ts(at),actor,current.tenantId(),current.policyId(),current.version(),current.state().name());
        if(n!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        VisibilityPolicyRecord updated=findVisibilityPolicy(current.tenantId(),current.policyId()).orElseThrow();
        insertVisibilityAudit(current.tenantId(),current.policyId(),current.state(),target,actor,reason,correlation,idempotency,updated.version(),at);
        return updated;
    }
    private void insertVisibilityAudit(String tenant,String policyId,VisibilityPolicyState previous,VisibilityPolicyState target,String actor,String reason,String correlation,String idem,long version,Instant at){
        jdbc.update("""
            insert into resource_visibility_policy_audits(tenant_id,audit_id,visibility_policy_id,previous_state,resulting_state,actor_id,reason,correlation_id,idempotency_key,resulting_version,occurred_at)
            values (?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
            """,tenant,id("visibility-audit",tenant,idem),policyId,previous==null?null:previous.name(),target.name(),actor,reason,correlation,idem,version,ts(at));
    }
    private void replaceFieldRules(VisibilityPolicyRecord p){
        jdbc.update("delete from resource_visibility_fields where tenant_id=? and visibility_policy_id=?",p.tenantId(),p.policyId());
        for(VisibilityFieldRule r:p.fieldRules())jdbc.update("""
            insert into resource_visibility_fields(tenant_id,visibility_policy_id,field_rule_id,field_path,minimum_visibility_level,sensitivity_level,masking_method,export_allowed,download_allowed,priority,version)
            values (?,?,?,?,?,?,?,?,?,?,?)
            """,p.tenantId(),p.policyId(),r.fieldRuleId(),r.fieldPath(),r.minimumVisibilityLevel().name(),r.sensitivityLevel().name(),r.maskingMethod().name(),r.exportAllowed(),r.downloadAllowed(),r.priority(),r.version());
    }

    @Override public Optional<PrincipalClearanceRecord> findPrincipalClearance(String tenantId,String clearanceId){return oneClearance("tenant_id=? and clearance_id=?",tenantId,clearanceId);}
    @Override public Optional<PrincipalClearanceRecord> findPrincipalClearanceByIdempotencyKey(String tenantId,String key){return oneClearance("tenant_id=? and idempotency_key=?",tenantId,key);}
    @Override public Optional<String> findPrincipalClearanceCreator(String tenantId,String clearanceId){return jdbc.query("select created_by from resource_principal_clearances where tenant_id=? and clearance_id=?",(rs,row)->rs.getString(1),tenantId,clearanceId).stream().findFirst();}
    private Optional<PrincipalClearanceRecord> oneClearance(String predicate,Object...args){return jdbc.query("select * from resource_principal_clearances where "+predicate+" limit 1",(rs,row)->clearance(rs),args).stream().findFirst();}

    @Override @Transactional public PrincipalClearanceRecord insertPrincipalClearance(PrincipalClearanceRecord c,String createdBy,String idempotency,String correlation){
        jdbc.update("""
            insert into resource_principal_clearances(tenant_id,clearance_id,principal_type,principal_id,clearance_level,valid_from,valid_to,clearance_status,created_by,approved_by,reason,idempotency_key,version,created_at,updated_at,updated_by)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,c.tenantId(),c.clearanceId(),c.principalType().name(),c.principalId(),c.clearanceLevel().name(),ts(c.validFrom()),ts(c.validTo()),c.status().name(),createdBy,blankToNull(c.approvedBy()),c.reason(),idempotency,c.version(),ts(c.createdAt()),ts(c.updatedAt()),createdBy);
        insertClearanceAudit(c.tenantId(),c.clearanceId(),null,c.status(),createdBy,c.reason(),correlation,idempotency,c.version(),c.createdAt());
        return c;
    }
    @Override @Transactional public PrincipalClearanceRecord transitionPrincipalClearance(PrincipalClearanceRecord current,ClearanceStatus target,String approvedBy,String actor,String reason,String correlation,String idempotency,Instant at){
        int n=jdbc.update("""
            update resource_principal_clearances set clearance_status=?,approved_by=?,version=version+1,updated_at=?,updated_by=?
             where tenant_id=? and clearance_id=? and version=? and clearance_status=?
            """,target.name(),blankToNull(approvedBy),ts(at),actor,current.tenantId(),current.clearanceId(),current.version(),current.status().name());
        if(n!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        PrincipalClearanceRecord updated=findPrincipalClearance(current.tenantId(),current.clearanceId()).orElseThrow();
        insertClearanceAudit(current.tenantId(),current.clearanceId(),current.status(),target,actor,reason,correlation,idempotency,updated.version(),at);
        return updated;
    }
    private void insertClearanceAudit(String tenant,String clearanceId,ClearanceStatus previous,ClearanceStatus target,String actor,String reason,String correlation,String idem,long version,Instant at){
        jdbc.update("""
            insert into resource_clearance_audits(tenant_id,audit_id,clearance_id,previous_status,resulting_status,actor_id,reason,correlation_id,idempotency_key,resulting_version,occurred_at)
            values (?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
            """,tenant,id("clearance-audit",tenant,idem),clearanceId,previous==null?null:previous.name(),target.name(),actor,reason,correlation,idem,version,ts(at));
    }

    @Override public Optional<SecurityStateChangeResult> findSecurityStateChange(ResourceRef ref,String idempotency){
        return jdbc.query("""
            select * from resource_security_state_mutation_audits where tenant_id=? and idempotency_key=? and resource_type=? and resource_id=? limit 1
            """,(rs,row)->securityChange(rs,ref),ref.tenantId(),idempotency,ref.resourceType().name(),ref.resourceId()).stream().findFirst();
    }

    @Override @Transactional public SecurityStateChangeResult changeSecurityState(SecurityStateChangeCommand command){
        ResourceRef ref=command.resourceRef();
        Map<String,Object> current=jdbc.queryForMap("""
            select security_state,resource_version from resource_descriptors
             where tenant_id=? and resource_type=? and resource_id=? for update
            """,ref.tenantId(),ref.resourceType().name(),ref.resourceId());
        ResourceSecurityState previous=ResourceSecurityState.valueOf(String.valueOf(current.get("security_state")));
        long version=((Number)current.get("resource_version")).longValue();
        if(version!=command.expectedResourceVersion())throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        if(previous==ResourceSecurityState.DELETED)throw new IllegalStateException("RESOURCE_SECURITY_STATE_TERMINAL");
        int n=jdbc.update("""
            update resource_descriptors set security_state=?,resource_version=resource_version+1,
             descriptor_hash=md5(descriptor_hash || ':' || ? || ':' || (resource_version+1)::text),updated_at=?
             where tenant_id=? and resource_type=? and resource_id=? and resource_version=?
            """,command.targetState().name(),command.targetState().name(),ts(command.requestedAt()),ref.tenantId(),ref.resourceType().name(),ref.resourceId(),command.expectedResourceVersion());
        if(n!=1)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        Long epoch=jdbc.queryForObject("""
            insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
            values (?,?,?,?,?,?)
            on conflict(tenant_id,resource_type,resource_id) do update set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,updated_at=excluded.updated_at,updated_by=excluded.updated_by
            returning resource_security_epoch
            """,Long.class,ref.tenantId(),ref.resourceType().name(),ref.resourceId(),1L,ts(command.requestedAt()),command.actorId());
        long resultingVersion=version+1;long resultingEpoch=epoch==null?1:epoch;
        jdbc.update("""
            insert into resource_security_state_mutation_audits(tenant_id,mutation_id,resource_type,resource_id,previous_state,resulting_state,expected_resource_version,resulting_resource_version,resulting_security_epoch,actor_id,reason,incident_id,correlation_id,idempotency_key,changed_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,ref.tenantId(),id("security-state",ref.tenantId(),command.idempotencyKey()),ref.resourceType().name(),ref.resourceId(),previous.name(),command.targetState().name(),version,resultingVersion,resultingEpoch,command.actorId(),command.reason(),blankToNull(command.incidentId()),command.correlationId(),command.idempotencyKey(),ts(command.requestedAt()));
        return new SecurityStateChangeResult(ref,previous,command.targetState(),resultingVersion,resultingEpoch,command.actorId(),command.reason(),command.requestedAt());
    }

    private ScopeGrantRecord grant(ResultSet rs)throws SQLException{return new ScopeGrantRecord(rs.getString("tenant_id"),rs.getString("scope_grant_id"),ScopePrincipalType.valueOf(rs.getString("principal_type")),rs.getString("principal_id"),rs.getString("permission_code"),ResourceType.valueOf(rs.getString("resource_type")),ScopeType.valueOf(rs.getString("scope_type")),nullToBlank(rs.getString("scope_ref_id")),VisibilityLevel.valueOf(rs.getString("visibility_level")),instant(rs,"valid_from"),nullableInstant(rs,"valid_to"),ScopeGrantSource.valueOf(rs.getString("grant_source")),rs.getString("grant_reason"),rs.getString("created_by"),nullToBlank(rs.getString("approved_by")),ScopeGrantState.valueOf(rs.getString("grant_state")),rs.getString("idempotency_key"),rs.getLong("version"),instant(rs,"created_at"),instant(rs,"updated_at"));}
    private ExplicitDenyRecord deny(ResultSet rs)throws SQLException{return new ExplicitDenyRecord(rs.getString("tenant_id"),rs.getString("scope_deny_id"),ScopePrincipalType.valueOf(rs.getString("principal_type")),rs.getString("principal_id"),nullToBlank(rs.getString("permission_code")),ResourceType.valueOf(rs.getString("resource_type")),ScopeType.valueOf(rs.getString("scope_type")),nullToBlank(rs.getString("scope_ref_id")),rs.getString("deny_reason"),DenySeverity.valueOf(rs.getString("severity")),instant(rs,"valid_from"),nullableInstant(rs,"valid_to"),rs.getString("created_by"),nullToBlank(rs.getString("approved_by")),ScopeDenyState.valueOf(rs.getString("deny_state")),rs.getString("idempotency_key"),rs.getLong("version"),instant(rs,"created_at"),instant(rs,"updated_at"));}
    private VisibilityPolicyRecord policy(ResultSet rs)throws SQLException{String tenant=rs.getString("tenant_id"),id=rs.getString("visibility_policy_id");List<VisibilityFieldRule> rules=jdbc.query("select * from resource_visibility_fields where tenant_id=? and visibility_policy_id=? order by priority,field_path",(r,row)->new VisibilityFieldRule(r.getString("field_rule_id"),r.getString("field_path"),VisibilityLevel.valueOf(r.getString("minimum_visibility_level")),SensitivityLevel.valueOf(r.getString("sensitivity_level")),MaskingMethod.valueOf(r.getString("masking_method")),r.getBoolean("export_allowed"),r.getBoolean("download_allowed"),r.getInt("priority"),r.getLong("version")),tenant,id);return new VisibilityPolicyRecord(tenant,id,ResourceType.valueOf(rs.getString("resource_type")),rs.getString("policy_name"),VisibilityLevel.valueOf(rs.getString("maximum_visibility")),SensitivityLevel.valueOf(rs.getString("maximum_sensitivity")),VisibilityPolicyState.valueOf(rs.getString("policy_state")),rules,rs.getLong("version"),rs.getString("created_by"),rs.getString("updated_by"),instant(rs,"created_at"),instant(rs,"updated_at"));}
    private PrincipalClearanceRecord clearance(ResultSet rs)throws SQLException{return new PrincipalClearanceRecord(rs.getString("tenant_id"),rs.getString("clearance_id"),ScopePrincipalType.valueOf(rs.getString("principal_type")),rs.getString("principal_id"),SensitivityLevel.valueOf(rs.getString("clearance_level")),instant(rs,"valid_from"),nullableInstant(rs,"valid_to"),ClearanceStatus.valueOf(rs.getString("clearance_status")),rs.getString("approved_by"),rs.getString("reason"),rs.getLong("version"),instant(rs,"created_at"),instant(rs,"updated_at"));}
    private SecurityStateChangeResult securityChange(ResultSet rs,ResourceRef ref)throws SQLException{return new SecurityStateChangeResult(ref,ResourceSecurityState.valueOf(rs.getString("previous_state")),ResourceSecurityState.valueOf(rs.getString("resulting_state")),rs.getLong("resulting_resource_version"),rs.getLong("resulting_security_epoch"),rs.getString("actor_id"),rs.getString("reason"),instant(rs,"changed_at"));}
    private static String id(String prefix,String tenant,String key){return prefix+"-"+UUID.nameUUIDFromBytes((tenant+":"+key).getBytes(StandardCharsets.UTF_8));}
    private static Timestamp ts(Instant value){return value==null?null:Timestamp.from(value);}
    private static Instant instant(ResultSet rs,String column)throws SQLException{return rs.getTimestamp(column).toInstant();}
    private static Instant nullableInstant(ResultSet rs,String column)throws SQLException{Timestamp value=rs.getTimestamp(column);return value==null?null:value.toInstant();}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String nullToBlank(String value){return value==null?"":value;}
}
