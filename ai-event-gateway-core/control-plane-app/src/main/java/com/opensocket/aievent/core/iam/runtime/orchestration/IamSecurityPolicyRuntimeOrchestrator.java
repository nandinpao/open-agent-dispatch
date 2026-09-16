package com.opensocket.aievent.core.iam.runtime.orchestration;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.iam.api.application.port.IamSecurityPolicyApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.MfaPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.PasswordPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.SessionPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.TokenPolicyRequest;
import com.opensocket.aievent.core.iam.api.response.SecurityPolicyResponse;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.outbox.IamTransactionalOutboxWriter;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class IamSecurityPolicyRuntimeOrchestrator implements IamSecurityPolicyApiPort {
    private static final TypeReference<Map<String,Object>> MAP_TYPE = new TypeReference<>() {};
    private final IamApiRuntimeDao dao;
    private final IamTenantOrganizationDao tenantDao;
    private final IamTransactionalOutboxWriter outbox;
    private final IamIdempotencyExecutor idempotency;
    private final ObjectMapper mapper;

    public IamSecurityPolicyRuntimeOrchestrator(IamApiRuntimeDao dao,IamTenantOrganizationDao tenantDao,IamTransactionalOutboxWriter outbox,IamIdempotencyExecutor idempotency,ObjectMapper mapper){
        this.dao=dao;this.tenantDao=tenantDao;this.outbox=outbox;this.idempotency=idempotency;this.mapper=mapper;
    }

    @Override public SecurityPolicyResponse read(IamApiRequestContext context){return in(context,()->response(context.activeTenantId()));}
    @Override public SecurityPolicyResponse updatePassword(PasswordPolicyRequest request,long expectedVersion,IamApiRequestContext context){return mutate("PASSWORD","security.policy.password",request,expectedVersion,context,passwordRow(context,request,expectedVersion));}
    @Override public SecurityPolicyResponse updateMfa(MfaPolicyRequest request,long expectedVersion,IamApiRequestContext context){Map<String,Object> row=base(context,expectedVersion);row.put("requiredForAdministrators",request.requiredForAdministrators());row.put("allowRecoveryCodes",request.allowRecoveryCodes());row.put("allowTotp",request.allowTotp());return mutate("MFA","security.policy.mfa",request,expectedVersion,context,row);}
    @Override public SecurityPolicyResponse updateSession(SessionPolicyRequest request,long expectedVersion,IamApiRequestContext context){Map<String,Object> row=base(context,expectedVersion);row.put("idleTimeoutSeconds",request.idleTimeoutMinutes()*60L);row.put("absoluteTimeoutSeconds",request.absoluteTimeoutMinutes()*60L);row.put("maxConcurrentSessions",request.maxConcurrentSessions());row.put("revokeOnPasswordChange",request.revokeOnPasswordChange());row.put("revokeOnMfaReset",request.revokeOnMfaReset());row.put("reauthenticationSeconds",request.reauthenticationMinutes()*60L);return mutate("SESSION","security.policy.session",request,expectedVersion,context,row);}
    @Override public SecurityPolicyResponse updateToken(TokenPolicyRequest request,long expectedVersion,IamApiRequestContext context){Map<String,Object> row=base(context,expectedVersion);row.put("personalMaxTtlSeconds",request.personalTokenMaxTtlDays()*86400L);row.put("serviceMaxTtlSeconds",request.serviceTokenMaxTtlDays()*86400L);row.put("maxActiveTokens",request.maxActiveTokens());row.put("requireServiceCidr",request.requireServiceAccountCidr());return mutate("TOKEN","security.policy.token",request,expectedVersion,context,row);}

    @Override
    public SecurityPolicyResponse restore(String policyKind,String revisionId,long expectedVersion,IamApiRequestContext context){
        String kind=normalizeKind(policyKind);
        Map<String,Object> idempotencyRequest=Map.of("policyKind",kind,"revisionId",revisionId,"expectedVersion",expectedVersion);
        return idempotency.execute(context.activeTenantId(),context.actorId(),"security.policy.restore."+kind.toLowerCase(),context.requireIdempotencyKey(),idempotencyRequest,200,SecurityPolicyResponse.class,()->in(context,()->{
            Map<String,Object> revision=dao.findSecurityPolicyRevision(context.activeTenantId(),revisionId);
            if(revision==null) throw IamApiException.notFound("SECURITY_POLICY_REVISION_NOT_FOUND","The selected Security Policy revision does not exist in this Tenant.");
            if(!kind.equalsIgnoreCase(text(revision,"policyKind"))) throw IamApiException.badRequest("SECURITY_POLICY_REVISION_KIND_MISMATCH","The selected revision belongs to a different Security Policy section.");
            Map<String,Object> snapshot=parsePolicy(text(revision,"policyJson"));
            archive(kind,current(kind,context.activeTenantId()),context,"Snapshot before restoring revision "+revisionId);
            Map<String,Object> row=restoreRow(kind,snapshot,context,expectedVersion);
            update(kind,row,expectedVersion);
            publish("SECURITY_POLICY_RESTORED",kind,revisionId,context);
            return response(context.activeTenantId());
        }));
    }

    private SecurityPolicyResponse mutate(String kind,String operation,Object request,long expectedVersion,IamApiRequestContext context,Map<String,Object> row){
        return idempotency.execute(context.activeTenantId(),context.actorId(),operation,context.requireIdempotencyKey(),request,200,SecurityPolicyResponse.class,()->in(context,()->{
            archive(kind,current(kind,context.activeTenantId()),context,context.auditReason());
            update(kind,row,expectedVersion);
            publish("SECURITY_POLICY_UPDATED",kind,"",context);
            return response(context.activeTenantId());
        }));
    }

    private void update(String kind,Map<String,Object> row,long expectedVersion){
        int changed=switch(kind){
            case "PASSWORD" -> dao.updatePasswordPolicy(row,expectedVersion);
            case "MFA" -> dao.updateMfaPolicy(row,expectedVersion);
            case "SESSION" -> dao.updateSessionPolicy(row,expectedVersion);
            case "TOKEN" -> dao.updateTokenPolicy(row,expectedVersion);
            default -> throw IamApiException.badRequest("SECURITY_POLICY_KIND_INVALID","Unknown Security Policy kind.");
        };
        if(changed!=1) throw IamApiException.conflict("IDENTITY_VERSION_CONFLICT","The Security Policy changed after it was loaded. Refresh and review the current values before saving.");
        tenantDao.incrementSecurityEpoch(text(row,"tenantId"),text(row,"updatedBy"));
    }

    private void archive(String kind,Map<String,Object> policy,IamApiRequestContext context,String reason){
        if(policy==null||policy.isEmpty()) return;
        Map<String,Object> row=new HashMap<>();
        row.put("revisionId","policy-revision-"+UUID.randomUUID());row.put("tenantId",context.activeTenantId());row.put("policyKind",kind);
        row.put("policyVersion",number(policy,"version",0));row.put("actorId",context.actorId());row.put("auditReason",reason);
        row.put("correlationId",context.correlationId());row.put("createdAt",context.requestedAt());
        try{row.put("policyJson",mapper.writeValueAsString(policy));}catch(Exception exception){throw new IllegalStateException("Unable to serialize Security Policy revision",exception);}
        dao.insertSecurityPolicyRevision(row);
    }

    private void publish(String eventType,String kind,String revisionId,IamApiRequestContext context){
        String eventId=UUID.randomUUID().toString();
        try{
            outbox.append(eventId,eventType,"TENANT",context.activeTenantId(),mapper.writeValueAsString(Map.of(
                    "tenantId",context.activeTenantId(),"policyKind",kind,"revisionId",revisionId,"actorId",context.actorId(),
                    "auditReason",context.auditReason(),"correlationId",context.correlationId())),context.requestedAt(),
                    context.activeTenantId(), context.correlationId(), context.actorId());
        }catch(Exception exception){throw new IllegalStateException(exception);}
    }

    private SecurityPolicyResponse response(String tenantId){
        Map<String,Object> password=nonNull(dao.findPasswordPolicy(tenantId));Map<String,Object> mfa=nonNull(dao.findMfaPolicy(tenantId));
        Map<String,Object> session=nonNull(dao.findSessionPolicy(tenantId));Map<String,Object> token=nonNull(dao.findTokenPolicy(tenantId));
        long version=Math.max(Math.max(number(password,"version",0),number(mfa,"version",0)),Math.max(number(session,"version",0),number(token,"version",0)));
        return new SecurityPolicyResponse(tenantId,Map.copyOf(password),Map.copyOf(mfa),Map.copyOf(session),Map.copyOf(token),version);
    }

    private Map<String,Object> current(String kind,String tenantId){return switch(kind){case "PASSWORD"->nonNull(dao.findPasswordPolicy(tenantId));case "MFA"->nonNull(dao.findMfaPolicy(tenantId));case "SESSION"->nonNull(dao.findSessionPolicy(tenantId));case "TOKEN"->nonNull(dao.findTokenPolicy(tenantId));default->Map.of();};}
    private Map<String,Object> restoreRow(String kind,Map<String,Object> snapshot,IamApiRequestContext context,long expectedVersion){
        Map<String,Object> row=base(context,expectedVersion);
        switch(kind){
            case "PASSWORD" -> {copy(row,snapshot,"minimumLength","maximumLength","requireUppercase","requireLowercase","requireNumber","requireSymbol","historyCount","failedAttemptThreshold","lockoutSeconds");}
            case "MFA" -> copy(row,snapshot,"requiredForAdministrators","allowRecoveryCodes","allowTotp");
            case "SESSION" -> copy(row,snapshot,"idleTimeoutSeconds","absoluteTimeoutSeconds","maxConcurrentSessions","revokeOnPasswordChange","revokeOnMfaReset","reauthenticationSeconds");
            case "TOKEN" -> copy(row,snapshot,"personalMaxTtlSeconds","serviceMaxTtlSeconds","maxActiveTokens","requireServiceCidr");
            default -> throw IamApiException.badRequest("SECURITY_POLICY_KIND_INVALID","Unknown Security Policy kind.");
        }
        return row;
    }

    private Map<String,Object> passwordRow(IamApiRequestContext context,PasswordPolicyRequest request,long expectedVersion){
        Map<String,Object> row=base(context,expectedVersion);row.put("minimumLength",request.minimumLength());row.put("maximumLength",256);
        row.put("requireUppercase",true);row.put("requireLowercase",true);row.put("requireNumber",true);row.put("requireSymbol",true);
        row.put("historyCount",request.historyCount());row.put("failedAttemptThreshold",request.failedAttemptThreshold());row.put("lockoutSeconds",request.lockoutMinutes()*60L);return row;
    }
    private Map<String,Object> base(IamApiRequestContext context,long expectedVersion){Map<String,Object> row=new HashMap<>();row.put("tenantId",context.activeTenantId());row.put("updatedAt",context.requestedAt());row.put("updatedBy",context.actorId());row.put("version",expectedVersion+1);return row;}
    private Map<String,Object> parsePolicy(String json){try{return mapper.readValue(json,MAP_TYPE);}catch(Exception exception){throw IamApiException.badRequest("SECURITY_POLICY_REVISION_INVALID","The stored Security Policy revision cannot be restored.");}}
    private <T>T in(IamApiRequestContext context,java.util.function.Supplier<T> action){return IamTenantContextHolder.withContext(new IamTenantExecutionContext(context.activeTenantId(),context.actorId()),action);}
    private static String normalizeKind(String value){String kind=value==null?"":value.trim().toUpperCase();if(!kind.equals("PASSWORD")&&!kind.equals("MFA")&&!kind.equals("SESSION")&&!kind.equals("TOKEN"))throw IamApiException.badRequest("SECURITY_POLICY_KIND_INVALID","Security Policy kind must be password, mfa, session or token.");return kind;}
    private static Map<String,Object> nonNull(Map<String,Object> value){return value==null?Map.of():value;}
    private static void copy(Map<String,Object> target,Map<String,Object> source,String... keys){for(String key:keys){if(source.containsKey(key))target.put(key,source.get(key));}}
    private static long number(Map<String,Object> row,String key,long fallback){Object value=row.get(key);if(value instanceof Number number)return number.longValue();try{return value==null?fallback:Long.parseLong(String.valueOf(value));}catch(NumberFormatException exception){return fallback;}}
    private static String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":String.valueOf(value);}
}
