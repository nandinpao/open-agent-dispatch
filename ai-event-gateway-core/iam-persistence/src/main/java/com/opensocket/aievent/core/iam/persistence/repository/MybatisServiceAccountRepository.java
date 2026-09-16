package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamTokenDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.token.application.port.out.ServiceAccountRepository;
import com.opensocket.aievent.core.iam.token.domain.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Duration;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisServiceAccountRepository implements ServiceAccountRepository {
    private final IamTokenDao dao;
    public MybatisServiceAccountRepository(IamTokenDao dao){this.dao=dao;}

    public Optional<ServiceAccount> find(String tenantId,ServiceAccountId id){
        return Optional.ofNullable(dao.findServiceAccount(tenantId,id.value())).map(this::domain);
    }

    public ServiceAccount save(ServiceAccount a,long expected){
        int n=expected==0?dao.insertServiceAccount(row(a)):dao.updateServiceAccount(row(a),expected);
        if(n!=1)throw new IamOptimisticLockException("ServiceAccount",a.serviceAccountId().value(),expected);
        return a;
    }

    private ServiceAccount domain(Map<String,Object>x){
        TokenScope restrictions=new TokenScope(csv(x,"permissionsCsv"),csv(x,"audiencesCsv"),csv(x,"apiPrefixesCsv"),csv(x,"cidrsCsv"));
        return ServiceAccount.reconstitute(
                string(x,"tenantId"),new ServiceAccountId(string(x,"serviceAccountId")),
                string(x,"accountName"),string(x,"description"),string(x,"ownerUserId"),string(x,"ownerDepartmentId"),
                string(x,"responsibilityBindingId"), restrictions,csv(x,"machineScopesCsv"),csv(x,"sourceSystemsCsv"),
                Duration.ofSeconds(longValue(x,"tokenMaxTtlSeconds")),intValue(x,"maxActiveTokens"),
                Duration.ofSeconds(longValue(x,"credentialMaxTtlSeconds")),intValue(x,"maxActiveCredentials"),
                intValue(x,"rateLimitPerMinute"),instant(x,"lastReviewedAt"),instant(x,"nextReviewAt"),
                ServiceAccountRiskLevel.valueOf(string(x,"riskLevel")),ServiceAccountStatus.valueOf(string(x,"status")),
                string(x,"statusReason"),instant(x,"createdAt"),instant(x,"updatedAt"),
                string(x,"createdBy"),string(x,"updatedBy"),longValue(x,"version"));
    }

    private Map<String,Object>row(ServiceAccount a){
        Map<String,Object>m=new HashMap<>();
        m.put("tenantId",a.tenantId());m.put("serviceAccountId",a.serviceAccountId().value());m.put("accountName",a.name());m.put("description",a.description());
        m.put("ownerUserId",a.ownerUserId());m.put("ownerDepartmentId",a.ownerDepartmentId());m.put("responsibilityBindingId",a.responsibilityBindingId().isBlank()?null:a.responsibilityBindingId());
        m.put("permissionsCsv",join(a.restrictions().permissions()));m.put("audiencesCsv",join(a.restrictions().audiences()));
        m.put("apiPrefixesCsv",join(a.restrictions().apiPrefixes()));m.put("cidrsCsv",join(a.restrictions().cidrs().stream().map(CidrBlock::notation).toList()));
        m.put("machineScopesCsv",join(a.machineScopes()));m.put("sourceSystemsCsv",join(a.allowedSourceSystems()));
        m.put("tokenMaxTtlSeconds",a.tokenMaxTtl().toSeconds());m.put("maxActiveTokens",a.maxActiveTokens());
        m.put("credentialMaxTtlSeconds",a.credentialMaxTtl().toSeconds());m.put("maxActiveCredentials",a.maxActiveCredentials());
        m.put("rateLimitPerMinute",a.rateLimitPerMinute());m.put("lastReviewedAt",a.lastReviewedAt());m.put("nextReviewAt",a.nextReviewAt());
        m.put("riskLevel",a.riskLevel().name());m.put("status",a.status().name());m.put("statusReason",a.statusReason());
        m.put("createdAt",a.createdAt());m.put("updatedAt",a.updatedAt());m.put("createdBy",a.createdBy());m.put("updatedBy",a.updatedBy());m.put("version",a.version());
        return m;
    }
}
