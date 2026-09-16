package com.opensocket.aievent.core.security.incident;

import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.iam.token.application.command.RevokeServiceAccountCredentialCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCredentialCommandPort;
import com.opensocket.aievent.core.lifecycle.TaskLifecycleService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 12.5 orchestration layer for operator Security Incident containment.
 * Positive authority remains Human RBAC + Resource Access; this service executes only after the
 * controller has authorized both the Incident Case and the target resource.
 */
@Service
public class SecurityIncidentControlService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ServiceAccountCredentialCommandPort credentials;
    private final AgentGovernanceService agents;
    private final TaskLifecycleService tasks;
    private final RuntimeIncidentControlPolicy runtimeControls;

    public SecurityIncidentControlService(NamedParameterJdbcTemplate jdbc,
            ServiceAccountCredentialCommandPort credentials,
            AgentGovernanceService agents,
            TaskLifecycleService tasks,
            RuntimeIncidentControlPolicy runtimeControls) {
        this.jdbc=jdbc; this.credentials=credentials; this.agents=agents; this.tasks=tasks; this.runtimeControls=runtimeControls;
    }

    @Transactional
    public SecurityIncidentCaseView createCase(String tenantId, CreateCaseCommand command, String actorId) {
        tenant(tenantId);
        CreateCaseCommand c=command==null?new CreateCaseCommand(null,null,null,null,null,null):command;
        String caseId="sic-"+UUID.randomUUID(); OffsetDateTime now=now();
        Scope scope=deriveScope(tenantId,c.rootTaskId(),c.sourceIncidentId(),c.sourceSystemId());
        jdbc.update("""
                insert into security_incident_cases(
                  tenant_id,case_id,title,severity,status,summary,source_incident_id,root_task_id,source_system_id,
                  owner_department_id,owner_group_id,opened_by,opened_at,last_action_at,version)
                values(:tenantId,:caseId,:title,:severity,'OPEN',:summary,:sourceIncidentId,:rootTaskId,:sourceSystemId,
                  :ownerDepartmentId,:ownerGroupId,:openedBy,:openedAt,:openedAt,1)
                """, params(tenantId).addValue("caseId",caseId).addValue("title",required(c.title(),"title"))
                .addValue("severity",severity(c.severity())).addValue("summary",text(c.summary()))
                .addValue("sourceIncidentId",text(c.sourceIncidentId())).addValue("rootTaskId",text(c.rootTaskId()))
                .addValue("sourceSystemId",first(c.sourceSystemId(),scope.sourceSystemId())).addValue("ownerDepartmentId",scope.departmentId())
                .addValue("ownerGroupId",scope.groupId()).addValue("openedBy",required(actorId,"actorId")).addValue("openedAt",now));
        if(!blank(c.sourceIncidentId())) jdbc.update("update incidents set security_case_id=:caseId where tenant_id=:tenantId and incident_id=:incidentId",
                params(tenantId).addValue("caseId",caseId).addValue("incidentId",c.sourceIncidentId()));
        return requireCase(tenantId,caseId);
    }

    @Transactional(readOnly=true)
    public List<SecurityIncidentCaseView> listCases(String tenantId,String status,String severity,int limit) {
        tenant(tenantId);
        runtimeControls.expireElapsedControls(tenantId);
        StringBuilder sql=new StringBuilder("select "+CASE_COLUMNS+" from security_incident_cases where tenant_id=:tenantId");
        MapSqlParameterSource p=params(tenantId).addValue("limit",safeLimit(limit));
        if(!blank(status)){sql.append(" and status=:status");p.addValue("status",status.trim().toUpperCase());}
        if(!blank(severity)){sql.append(" and severity=:severity");p.addValue("severity",severity(severity));}
        sql.append(" order by case when severity='CRITICAL' then 4 when severity='HIGH' then 3 when severity='MEDIUM' then 2 else 1 end desc, opened_at desc limit :limit");
        return jdbc.query(sql.toString(),p,CASE_MAPPER);
    }

    @Transactional(readOnly=true)
    public SecurityIncidentCaseDetails details(String tenantId,String caseId) {
        tenant(tenantId);
        runtimeControls.expireElapsedControls(tenantId);
        SecurityIncidentCaseView incident=requireCase(tenantId,caseId);
        List<SecurityControlView> controls=jdbc.query("select "+CONTROL_COLUMNS+" from security_resource_controls where tenant_id=:tenantId and case_id=:caseId order by started_at desc",
                params(tenantId).addValue("caseId",caseId),CONTROL_MAPPER);
        List<SecurityControlActionView> actions=jdbc.query("select "+ACTION_COLUMNS+" from security_control_action_evidence where tenant_id=:tenantId and case_id=:caseId order by occurred_at desc,action_id desc limit 500",
                params(tenantId).addValue("caseId",caseId),ACTION_MAPPER);
        return new SecurityIncidentCaseDetails(incident,controls,actions);
    }

    @Transactional
    public SecurityIncidentCaseDetails apply(String tenantId,String caseId,ApplyControlCommand command,String actorId,String correlationId,String decisionId) {
        tenant(tenantId);
        runtimeControls.expireElapsedControls(tenantId);
        SecurityIncidentCaseView incident=requireOpenCase(tenantId,caseId);
        ApplyControlCommand c=require(command); String targetType=upper(c.targetType()); String action=upper(c.action()); String targetId=required(c.targetId(),"targetId");
        validateAction(targetType,action,c.limitPerMinute(),c.expiresAt());
        String parentTargetId="CREDENTIAL".equals(targetType)?credentialParent(tenantId,targetType,targetId):text(c.parentTargetId());
        String before=snapshot(tenantId,targetType,targetId);
        boolean sideEffectApplied=shouldApplyReversibleSideEffect(targetType,action,before);
        String controlId=null;
        if(stateful(action)) controlId=createControl(tenantId,caseId,targetType,targetId,parentTargetId,action,c.limitPerMinute(),c.expiresAt(),required(c.reason(),"reason"),actorId,before,sideEffectApplied);
        executeAction(tenantId,targetType,targetId,parentTargetId,action,c.reason(),actorId,correlationId,sideEffectApplied);
        String after=snapshot(tenantId,targetType,targetId);
        appendEvidence(tenantId,caseId,controlId,targetType,targetId,action,actorId,c.reason(),before,after,decisionId,correlationId);
        OffsetDateTime at=now();
        jdbc.update("update security_incident_cases set status=case when status='OPEN' then 'CONTAINED' else status end,contained_at=coalesce(contained_at,:at),last_action_at=:at,version=version+1 where tenant_id=:tenantId and case_id=:caseId",
                params(tenantId).addValue("caseId",caseId).addValue("at",at));
        return details(tenantId,incident.caseId());
    }

    @Transactional
    public SecurityIncidentCaseDetails release(String tenantId,String caseId,String controlId,String reason,String actorId,String correlationId,String decisionId) {
        tenant(tenantId);
        runtimeControls.expireElapsedControls(tenantId);
        requireOpenCase(tenantId,caseId); SecurityControlView control=requireActiveControl(tenantId,caseId,controlId);
        String before=snapshot(tenantId,control.targetType(),control.targetId()); OffsetDateTime at=now();
        int updated=jdbc.update("""
                update security_resource_controls set status='RELEASED',released_at=:at,released_by=:actor,release_reason=:reason,version=version+1
                 where tenant_id=:tenantId and case_id=:caseId and control_id=:controlId and status='ACTIVE'
                """,params(tenantId).addValue("caseId",caseId).addValue("controlId",controlId).addValue("at",at)
                .addValue("actor",actorId).addValue("reason",required(reason,"reason")));
        if(updated!=1) throw new IllegalStateException("Security control is no longer active: "+controlId);
        releaseSideEffect(tenantId,control,reason,actorId);
        String after=snapshot(tenantId,control.targetType(),control.targetId());
        appendEvidence(tenantId,caseId,controlId,control.targetType(),control.targetId(),releaseAction(control.controlType()),actorId,reason,before,after,decisionId,correlationId);
        jdbc.update("update security_incident_cases set last_action_at=:at,version=version+1 where tenant_id=:tenantId and case_id=:caseId",
                params(tenantId).addValue("caseId",caseId).addValue("at",at));
        return details(tenantId,caseId);
    }

    @Transactional
    public SecurityIncidentCaseView resolve(String tenantId,String caseId,String reason,String actorId,String correlationId) {
        tenant(tenantId);
        runtimeControls.expireElapsedControls(tenantId);
        SecurityIncidentCaseView incident=requireOpenCase(tenantId,caseId);
        Integer active=jdbc.queryForObject("select count(*) from security_resource_controls where tenant_id=:tenantId and case_id=:caseId and status='ACTIVE' and (expires_at is null or expires_at>now())",
                params(tenantId).addValue("caseId",caseId),Integer.class);
        if(active!=null&&active>0) throw new IllegalStateException("Release all active containment controls before resolving the Security Incident Case.");
        OffsetDateTime at=now();
        jdbc.update("update security_incident_cases set status='RESOLVED',resolved_at=:at,resolution_reason=:reason,last_action_at=:at,version=version+1 where tenant_id=:tenantId and case_id=:caseId",
                params(tenantId).addValue("caseId",caseId).addValue("at",at).addValue("reason",required(reason,"reason")));
        return requireCase(tenantId,incident.caseId());
    }

    @Transactional
    public int expireElapsedControls(String tenantId) { tenant(tenantId); runtimeControls.expireElapsedControls(tenantId); return 1; }

    private void executeAction(String tenantId,String targetType,String targetId,String parentTargetId,String action,String reason,String actorId,String correlationId,boolean sideEffectApplied) {
        switch(targetType) {
            case "CREDENTIAL" -> {
                if("REVOKE".equals(action)) credentials.revoke(new RevokeServiceAccountCredentialCommand(tenantId,required(parentTargetId,"parentTargetId"),targetId,required(reason,"reason"),actorId,correlationId));
            }
            case "SERVICE_ACCOUNT" -> { /* THROTTLE/SUSPEND are reversible runtime controls. */ }
            case "AGENT" -> {
                if("SUSPEND".equals(action) && sideEffectApplied) agents.suspendAgent(targetId,actorId,reason);
                else if("REVOKE".equals(action)) agents.revokeAgent(targetId,actorId,reason);
            }
            case "TASK" -> {
                switch(action) {
                    case "HOLD","BLOCK_RETRY" -> { if(sideEffectApplied) tasks.hold(targetId,reason); }
                    case "CANCEL" -> tasks.cancel(targetId,reason);
                    case "REASSIGN" -> tasks.reassign(targetId,reason);
                    case "FORCE_FAIL" -> tasks.forceFail(targetId,reason);
                    default -> { }
                }
            }
            case "SOURCE_SYSTEM" -> { /* QUARANTINE is enforced by Event Intake runtime policy. */ }
            default -> throw new IllegalArgumentException("Unsupported incident target type: "+targetType);
        }
    }

    private void releaseSideEffect(String tenantId,SecurityControlView control,String reason,String actorId) {
        if(!control.sideEffectApplied()) return;
        if("AGENT".equals(control.targetType()) && "SUSPEND".equals(control.controlType())) agents.enableAgent(control.targetId(),actorId,reason);
        if("TASK".equals(control.targetType()) && ("HOLD".equals(control.controlType())||"BLOCK_RETRY".equals(control.controlType()))) {
            if(!runtimeControls.taskDispatchBlocked(tenantId,control.targetId())) tasks.resume(control.targetId(),reason);
        }
    }

    private String createControl(String tenantId,String caseId,String targetType,String targetId,String parentTargetId,String action,Integer limit,OffsetDateTime expiresAt,String reason,String actorId,String originalState,boolean sideEffectApplied) {
        runtimeControls.expireElapsedControls(tenantId);
        Integer existing=jdbc.queryForObject("select count(*) from security_resource_controls where tenant_id=:tenantId and target_type=:targetType and target_id=:targetId and control_type=:controlType and status='ACTIVE' and (expires_at is null or expires_at>now())",
                params(tenantId).addValue("targetType",targetType).addValue("targetId",targetId).addValue("controlType",action),Integer.class);
        if(existing!=null&&existing>0) throw new IllegalStateException("An active "+action+" control already exists for "+targetType+" "+targetId);
        String id="ctl-"+UUID.randomUUID();
        jdbc.update("""
                insert into security_resource_controls(tenant_id,control_id,case_id,target_type,target_id,parent_target_id,control_type,status,limit_per_minute,reason,requested_by,started_at,expires_at,original_state,side_effect_applied,version)
                values(:tenantId,:controlId,:caseId,:targetType,:targetId,:parentTargetId,:controlType,'ACTIVE',:limit,:reason,:actor,:startedAt,:expiresAt,cast(:originalState as jsonb),:sideEffectApplied,1)
                """,params(tenantId).addValue("controlId",id).addValue("caseId",caseId).addValue("targetType",targetType).addValue("targetId",targetId)
                .addValue("parentTargetId",text(parentTargetId)).addValue("controlType",action).addValue("limit",limit).addValue("reason",reason)
                .addValue("actor",actorId).addValue("startedAt",now()).addValue("expiresAt",expiresAt).addValue("originalState",json(originalState)).addValue("sideEffectApplied",sideEffectApplied));
        return id;
    }

    private void appendEvidence(String tenantId,String caseId,String controlId,String targetType,String targetId,String action,String actor,String reason,String before,String after,String decisionId,String correlationId) {
        jdbc.update("""
                insert into security_control_action_evidence(tenant_id,action_id,case_id,control_id,target_type,target_id,action_type,actor_id,reason,before_state,after_state,authorization_decision_id,correlation_id,occurred_at)
                values(:tenantId,:actionId,:caseId,:controlId,:targetType,:targetId,:actionType,:actor,:reason,cast(:beforeState as jsonb),cast(:afterState as jsonb),:decisionId,:correlationId,:occurredAt)
                """,params(tenantId).addValue("actionId","act-"+UUID.randomUUID()).addValue("caseId",caseId).addValue("controlId",text(controlId))
                .addValue("targetType",targetType).addValue("targetId",targetId).addValue("actionType",action).addValue("actor",actor)
                .addValue("reason",required(reason,"reason")).addValue("beforeState",json(before)).addValue("afterState",json(after))
                .addValue("decisionId",text(decisionId)).addValue("correlationId",text(correlationId)).addValue("occurredAt",now()));
    }

    private String snapshot(String tenantId,String targetType,String targetId) {
        try {
            return switch(targetType) {
                case "CREDENTIAL" -> jdbc.queryForObject("select jsonb_build_object('credentialId',credential_id,'serviceAccountId',service_account_id,'clientId',client_id,'status',status,'expiresAt',expires_at,'lastUsedAt',last_used_at)::text from token_service_account_credentials where tenant_id=:tenantId and credential_id=:id",params(tenantId).addValue("id",targetId),String.class);
                case "SERVICE_ACCOUNT" -> jdbc.queryForObject("select jsonb_build_object('serviceAccountId',service_account_id,'status',status,'riskLevel',risk_level,'rateLimitPerMinute',rate_limit_per_minute,'ownerDepartmentId',owner_department_id)::text from token_service_accounts where tenant_id=:tenantId and service_account_id=:id",params(tenantId).addValue("id",targetId),String.class);
                case "AGENT" -> jdbc.queryForObject("select jsonb_build_object('agentId',agent_id,'approvalStatus',approval_status,'enabled',enabled,'riskStatus',risk_status,'ownerDepartmentId',owner_department_id,'ownerGroupId',owner_group_id)::text from agent_profiles where tenant_id=:tenantId and agent_id=:id",params(tenantId).addValue("id",targetId),String.class);
                case "TASK" -> jdbc.queryForObject("select jsonb_build_object('taskId',task_id,'status',status,'priority',priority,'failureDomain',failure_domain,'ownerDepartmentId',owner_department_id,'ownerGroupId',owner_group_id,'assignedAgentId',assigned_agent_id)::text from tasks where tenant_id=:tenantId and task_id=:id",params(tenantId).addValue("id",targetId),String.class);
                case "SOURCE_SYSTEM" -> jdbc.queryForObject("select jsonb_build_object('sourceSystemId',source_system_id,'status',status,'ownerDepartmentId',owner_department_id,'ownerGroupId',owner_group_id)::text from source_systems where tenant_id=:tenantId and source_system_id=:id",params(tenantId).addValue("id",targetId),String.class);
                default -> "{}";
            };
        } catch (EmptyResultDataAccessException ex) { throw new IllegalArgumentException(targetType+" not found: "+targetId); }
    }

    private Scope deriveScope(String tenantId,String rootTaskId,String incidentId,String sourceSystemId) {
        if(!blank(rootTaskId)) {
            try { return jdbc.queryForObject("select coalesce(owner_department_id,origin_department_id,'UNASSIGNED'),coalesce(owner_group_id,origin_group_id),coalesce(origin_workload_source_system,source_system) from tasks where tenant_id=:tenantId and task_id=:id",
                    params(tenantId).addValue("id",rootTaskId),(rs,n)->new Scope(rs.getString(1),rs.getString(2),rs.getString(3))); } catch(EmptyResultDataAccessException ignored) { }
        }
        if(!blank(incidentId)) {
            try { return jdbc.queryForObject("select coalesce(owner_department_id,'UNASSIGNED'),owner_group_id,source_system from incidents where tenant_id=:tenantId and incident_id=:id",
                    params(tenantId).addValue("id",incidentId),(rs,n)->new Scope(rs.getString(1),rs.getString(2),rs.getString(3))); } catch(EmptyResultDataAccessException ignored) { }
        }
        if(!blank(sourceSystemId)) {
            try { return jdbc.queryForObject("select coalesce(owner_department_id,'UNASSIGNED'),owner_group_id,source_system_id from source_systems where tenant_id=:tenantId and source_system_id=:id",
                    params(tenantId).addValue("id",sourceSystemId),(rs,n)->new Scope(rs.getString(1),rs.getString(2),rs.getString(3))); } catch(EmptyResultDataAccessException ignored) { }
        }
        return new Scope("UNASSIGNED",null,text(sourceSystemId));
    }

    @Transactional(readOnly=true)
    public String credentialServiceAccountId(String tenantId,String credentialId) {
        tenant(tenantId);
        return credentialParent(tenantId,"CREDENTIAL",required(credentialId,"credentialId"));
    }

    private String credentialParent(String tenantId,String targetType,String targetId) {
        if(!"CREDENTIAL".equals(targetType)) return null;
        try { return jdbc.queryForObject("select service_account_id from token_service_account_credentials where tenant_id=:tenantId and credential_id=:id",params(tenantId).addValue("id",targetId),String.class); }
        catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("Credential not found: "+targetId);}
    }

    private SecurityIncidentCaseView requireCase(String tenantId,String caseId) {
        try { return jdbc.queryForObject("select "+CASE_COLUMNS+" from security_incident_cases where tenant_id=:tenantId and case_id=:caseId",params(tenantId).addValue("caseId",required(caseId,"caseId")),CASE_MAPPER); }
        catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("Security Incident Case not found: "+caseId);}
    }
    private SecurityIncidentCaseView requireOpenCase(String tenantId,String caseId){SecurityIncidentCaseView c=requireCase(tenantId,caseId);if("RESOLVED".equals(c.status()))throw new IllegalStateException("Resolved Security Incident Case cannot be mutated.");return c;}
    private SecurityControlView requireActiveControl(String tenantId,String caseId,String controlId){try{return jdbc.queryForObject("select "+CONTROL_COLUMNS+" from security_resource_controls where tenant_id=:tenantId and case_id=:caseId and control_id=:controlId and status='ACTIVE' and (expires_at is null or expires_at>now())",params(tenantId).addValue("caseId",caseId).addValue("controlId",controlId),CONTROL_MAPPER);}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("Active Security control not found: "+controlId);}}

    private void tenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id',?,true)",String.class,tenantId);
    }

    private static boolean shouldApplyReversibleSideEffect(String targetType,String action,String beforeState){
        if("AGENT".equals(targetType) && "SUSPEND".equals(action)) return beforeState!=null && beforeState.contains("\"enabled\" : true");
        if("TASK".equals(targetType) && ("HOLD".equals(action)||"BLOCK_RETRY".equals(action))) return beforeState==null || (!beforeState.contains("WAITING_HUMAN") && !beforeState.contains("BLOCKED"));
        return false;
    }

    private static boolean stateful(String action){return switch(action){case "THROTTLE","SUSPEND","HOLD","BLOCK_RETRY","QUARANTINE"->true;default->false;};}
    private static String releaseAction(String type){return switch(type){case "HOLD"->"RELEASE_HOLD";case "BLOCK_RETRY"->"RELEASE_BLOCK_RETRY";case "QUARANTINE"->"RELEASE_QUARANTINE";default->"RELEASE_CONTROL";};}
    private static void validateAction(String type,String action,Integer limit,OffsetDateTime expiresAt){boolean ok=switch(type){case "CREDENTIAL"->List.of("THROTTLE","SUSPEND","REVOKE").contains(action);case "SERVICE_ACCOUNT"->List.of("THROTTLE","SUSPEND").contains(action);case "AGENT"->List.of("SUSPEND","REVOKE").contains(action);case "TASK"->List.of("HOLD","BLOCK_RETRY","CANCEL","REASSIGN","FORCE_FAIL").contains(action);case "SOURCE_SYSTEM"->"QUARANTINE".equals(action);default->false;};if(!ok)throw new IllegalArgumentException("Unsupported action "+action+" for "+type);if("THROTTLE".equals(action)&&(limit==null||limit<1))throw new IllegalArgumentException("limitPerMinute must be positive for THROTTLE");if(expiresAt!=null && ("AGENT".equals(type)||"TASK".equals(type)))throw new IllegalArgumentException("Agent/Task controls require explicit release so reversible domain state cannot expire without restoration evidence");}
    private static ApplyControlCommand require(ApplyControlCommand c){if(c==null)throw new IllegalArgumentException("Control request body is required");return c;}
    private static int safeLimit(int v){return Math.max(1,Math.min(v,500));}
    private static String severity(String v){String x=upper(blank(v)?"MEDIUM":v);if(!List.of("LOW","MEDIUM","HIGH","CRITICAL").contains(x))throw new IllegalArgumentException("severity must be LOW, MEDIUM, HIGH or CRITICAL");return x;}
    private static String upper(String v){return required(v,"value").toUpperCase(java.util.Locale.ROOT);}
    private static String required(String v,String f){if(blank(v))throw new IllegalArgumentException(f+" is required");return v.trim();}
    private static String text(String v){return blank(v)?null:v.trim();}
    private static String first(String a,String b){return blank(a)?b:a.trim();}
    private static boolean blank(String v){return v==null||v.isBlank();}
    private static String json(String v){return blank(v)?"{}":v;}
    private static OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private static MapSqlParameterSource params(String tenantId){return new MapSqlParameterSource("tenantId",required(tenantId,"tenantId"));}

    private static final String CASE_COLUMNS="tenant_id,case_id,title,severity,status,summary,source_incident_id,root_task_id,source_system_id,owner_department_id,owner_group_id,opened_by,opened_at,contained_at,resolved_at,resolution_reason,last_action_at,version";
    private static final String CONTROL_COLUMNS="tenant_id,control_id,case_id,target_type,target_id,parent_target_id,control_type,status,limit_per_minute,reason,requested_by,started_at,expires_at,released_at,released_by,release_reason,original_state::text,side_effect_applied,version";
    private static final String ACTION_COLUMNS="tenant_id,action_id,case_id,control_id,target_type,target_id,action_type,actor_id,reason,before_state::text,after_state::text,authorization_decision_id,correlation_id,occurred_at";
    private static final org.springframework.jdbc.core.RowMapper<SecurityIncidentCaseView> CASE_MAPPER=(rs,n)->new SecurityIncidentCaseView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getObject(13,OffsetDateTime.class),rs.getObject(14,OffsetDateTime.class),rs.getObject(15,OffsetDateTime.class),rs.getString(16),rs.getObject(17,OffsetDateTime.class),rs.getLong(18));
    private static final org.springframework.jdbc.core.RowMapper<SecurityControlView> CONTROL_MAPPER=(rs,n)->new SecurityControlView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),(Integer)rs.getObject(9),rs.getString(10),rs.getString(11),rs.getObject(12,OffsetDateTime.class),rs.getObject(13,OffsetDateTime.class),rs.getObject(14,OffsetDateTime.class),rs.getString(15),rs.getString(16),rs.getString(17),rs.getBoolean(18),rs.getLong(19));
    private static final org.springframework.jdbc.core.RowMapper<SecurityControlActionView> ACTION_MAPPER=(rs,n)->new SecurityControlActionView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getObject(14,OffsetDateTime.class));

    private record Scope(String departmentId,String groupId,String sourceSystemId){}
    public record CreateCaseCommand(String title,String severity,String summary,String sourceIncidentId,String rootTaskId,String sourceSystemId){}
    public record ApplyControlCommand(String targetType,String targetId,String parentTargetId,String action,Integer limitPerMinute,OffsetDateTime expiresAt,String reason){}
    public record SecurityIncidentCaseView(String tenantId,String caseId,String title,String severity,String status,String summary,String sourceIncidentId,String rootTaskId,String sourceSystemId,String ownerDepartmentId,String ownerGroupId,String openedBy,OffsetDateTime openedAt,OffsetDateTime containedAt,OffsetDateTime resolvedAt,String resolutionReason,OffsetDateTime lastActionAt,long version){}
    public record SecurityControlView(String tenantId,String controlId,String caseId,String targetType,String targetId,String parentTargetId,String controlType,String status,Integer limitPerMinute,String reason,String requestedBy,OffsetDateTime startedAt,OffsetDateTime expiresAt,OffsetDateTime releasedAt,String releasedBy,String releaseReason,String originalState,boolean sideEffectApplied,long version){}
    public record SecurityControlActionView(String tenantId,String actionId,String caseId,String controlId,String targetType,String targetId,String actionType,String actorId,String reason,String beforeState,String afterState,String authorizationDecisionId,String correlationId,OffsetDateTime occurredAt){}
    public record SecurityIncidentCaseDetails(SecurityIncidentCaseView incident,List<SecurityControlView> controls,List<SecurityControlActionView> actions){}
}
