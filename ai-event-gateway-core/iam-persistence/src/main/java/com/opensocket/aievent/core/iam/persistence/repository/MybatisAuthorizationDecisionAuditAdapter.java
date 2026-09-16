package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.join;

import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.rbac.application.port.out.AuthorizationDecisionAuditPort;
import com.opensocket.aievent.core.iam.security.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisAuthorizationDecisionAuditAdapter implements AuthorizationDecisionAuditPort {
    private final IamRbacDao dao; public MybatisAuthorizationDecisionAuditAdapter(IamRbacDao dao){this.dao=dao;}
    public void append(AuthorizationRequest request,AuthorizationDecision decision,long policyVersion){Map<String,Object> row=new HashMap<>();row.put("decisionId",decision.decisionId());row.put("tenantId",request.activeTenant().scope()==TenantRef.Scope.TENANT?request.activeTenant().tenantId():null);row.put("principalType",request.principal().principalType().name());row.put("principalId",request.principal().principalId());row.put("permissionCode",request.permission());row.put("resourceType",request.resourceType());row.put("resourceId",request.resourceId());row.put("requestedScopeType",request.requestedScopeType());row.put("requestedScopeId",request.requestedScopeId());row.put("decision",decision.effect().name());row.put("reasonCode",decision.reasonCode());row.put("matchedBindingIds",join(decision.matchedBindingIds()));row.put("matchedRoleIds",join(decision.matchedRoleIds()));row.put("effectiveScopeType",decision.effectiveScopeType());row.put("effectiveScopeId",decision.effectiveScopeId());row.put("policyVersion",policyVersion);row.put("globalEpoch",decision.evaluatedEpoch().globalEpoch());row.put("tenantEpoch",decision.evaluatedEpoch().tenantEpoch());row.put("principalEpoch",decision.evaluatedEpoch().principalEpoch());row.put("correlationId",request.requestContext().getOrDefault("correlationId",""));row.put("decidedAt",decision.evaluatedAt());if(dao.insertDecisionAudit(row)!=1)throw new IllegalStateException("RBAC_DECISION_AUDIT_INSERT_FAILED");}
}
