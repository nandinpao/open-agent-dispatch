package com.opensocket.aievent.core.iam.persistence.config;

import com.opensocket.aievent.core.iam.persistence.cache.*;
import com.opensocket.aievent.core.iam.persistence.shadow.MybatisShadowDecisionRecorder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.rbac.application.port.in.*;
import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.rbac.application.service.*;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationRequest;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.Clock;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration(after={IamPersistenceAutoConfiguration.class,IamAuthenticationApplicationServiceConfiguration.class})
@EnableConfigurationProperties(IamRbacProperties.class)
@ConditionalOnProperty(prefix="aeg.iam.rbac",name="enabled",havingValue="true")
public class IamRbacApplicationServiceConfiguration {
    @Bean @ConditionalOnMissingBean AuthorizationGrantCachePort authorizationGrantCachePort(IamRbacProperties p){return new CaffeineAuthorizationGrantCache(p.getCacheMaximumSize(),p.getCacheTtl());}
    @Bean @ConditionalOnMissingBean ShadowDecisionRecorderPort shadowDecisionRecorderPort(com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao dao,IamRbacProperties p){return new MybatisShadowDecisionRecorder(dao,p.getShadowSampleQueueCapacity());}
    @Bean @ConditionalOnMissingBean CacheInvalidationPort cacheInvalidationPort(ObjectProvider<StringRedisTemplate> redis,IamRbacProperties p){StringRedisTemplate template=redis.getIfAvailable();return template==null?ignored -> { }:new RedisRbacCacheInvalidationPublisher(template,p.getInvalidationChannel());}
    @Bean @ConditionalOnBean(RedisConnectionFactory.class) @ConditionalOnMissingBean(name="rbacRedisMessageListenerContainer")
    RedisMessageListenerContainer rbacRedisMessageListenerContainer(RedisConnectionFactory factory,AuthorizationGrantCachePort cache,IamRbacProperties p){RedisMessageListenerContainer container=new RedisMessageListenerContainer();container.setConnectionFactory(factory);container.addMessageListener(new RbacCacheInvalidationMessageListener(cache),new ChannelTopic(p.getInvalidationChannel()));return container;}
    @Bean @ConditionalOnMissingBean AuthorizationPort authorizationPort(PermissionCatalogRepository permissions,PrincipalExpansionPort expansion,OrganizationScopePort scopes,PrincipalRoleBindingRepository bindings,RoleRepository roles,RolePermissionRepository rolePermissions,PolicyVersionRepository versions,SecurityEpochAuthorityPort epochs,AuthorizationDecisionAuditPort audit,AuthorizationGrantCachePort cache,Clock clock,PlatformTransactionManager tx){return authorizationTransactionBoundary(new AuthorizationService(permissions,expansion,scopes,bindings,roles,rolePermissions,versions,epochs,audit,cache,clock),tx);}
    @Bean @ConditionalOnMissingBean EffectivePermissionScopePort effectivePermissionScopePort(PermissionCatalogRepository permissions,PrincipalExpansionPort expansion,PrincipalRoleBindingRepository bindings,RoleRepository roles,RolePermissionRepository rolePermissions,PolicyVersionRepository versions,SecurityEpochAuthorityPort epochs,Clock clock,PlatformTransactionManager tx){return proxy(new EffectivePermissionScopeService(permissions,expansion,bindings,roles,rolePermissions,versions,epochs,clock),EffectivePermissionScopePort.class,tx);}
    @Bean @ConditionalOnMissingBean ShadowAuthorizationPort shadowAuthorizationPort(ShadowDecisionRecorderPort recorder,IamRbacProperties p,Clock clock,PlatformTransactionManager tx){return proxy(new ShadowAuthorizationService(recorder,p.getShadowMatchSamplingRate(),p.getShadowMaxMetadataEntries(),clock),ShadowAuthorizationPort.class,tx);}
    @Bean @ConditionalOnMissingBean PermissionCatalogAdministrationPort permissionCatalogAdministrationPort(PermissionCatalogAdministrationRepository repository,PlatformTransactionManager tx){return instanceProxy(new PermissionCatalogAdministrationService(repository),PermissionCatalogAdministrationPort.class,tx,"permission-catalog");}
    @Bean @ConditionalOnMissingBean EntryPointAuthorityAdministrationPort entryPointAuthorityAdministrationPort(EntryPointAuthorityRepository repository,PlatformTransactionManager tx){return proxy(new EntryPointAuthorityAdministrationService(repository),EntryPointAuthorityAdministrationPort.class,tx);}
    @Bean @ConditionalOnMissingBean RbacAdministrationPort rbacAdministrationPort(RoleRepository roles,PermissionCatalogRepository permissions,RolePermissionRepository rolePermissions,PrincipalRoleBindingRepository bindings,PolicyVersionRepository versions,RbacEventPublisher events,CacheInvalidationPort invalidation,Clock clock,PlatformTransactionManager tx){return proxy(new RbacAdministrationService(roles,permissions,rolePermissions,bindings,versions,events,invalidation,clock),RbacAdministrationPort.class,tx);}
    @Bean @ConditionalOnMissingBean SecurityEpochReconciliationService securityEpochReconciliationService(SecurityEpochAuthorityPort authority,AuthorizationGrantCachePort cache){return new SecurityEpochReconciliationService(authority,cache);}

    /**
     * Authorization owns its Tenant persistence + transaction boundary.
     *
     * <p>Keep this explicit instead of composing two AOP advices. The verified
     * AuthorizationRequest chooses the Tenant, that Tenant context is installed
     * first, and the complete decision (including epoch reads and decision audit)
     * then runs inside one Spring transaction. This makes HTTP R3 filters,
     * controller guards and non-HTTP callers deterministic and independent of
     * outer request ThreadLocals/advice ordering.</p>
     */
    private static AuthorizationPort authorizationTransactionBoundary(
            AuthorizationPort target, PlatformTransactionManager manager) {
        TransactionTemplate transactions = new TransactionTemplate(manager);
        transactions.setName("iam-rbac:AuthorizationPort");
        return request -> {
            if (request == null) throw new IllegalStateException("AUTHORIZATION_REQUEST_REQUIRED");
            String tenant = request.activeTenant().scope() == TenantRef.Scope.TENANT
                    ? request.activeTenant().tenantId() : "INSTANCE";
            String actor = request.principal().principalId();
            return IamTenantContextHolder.withContext(
                    new IamTenantExecutionContext(tenant, actor),
                    () -> {
                        com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision decision =
                                transactions.execute(status -> target.authorize(request));
                        if (decision == null) {
                            throw new IllegalStateException("AUTHORIZATION_DECISION_REQUIRED");
                        }
                        return decision;
                    });
        };
    }

    private static <T>T instanceProxy(T target,Class<T> api,PlatformTransactionManager manager,String actor){DefaultTransactionAttribute a=new DefaultTransactionAttribute();a.setName("iam-rbac:"+api.getSimpleName());MatchAlwaysTransactionAttributeSource source=new MatchAlwaysTransactionAttributeSource();source.setTransactionAttribute(a);TransactionInterceptor transaction=new TransactionInterceptor(manager,source);MethodInterceptor scope=invocation->IamTenantContextHolder.withContext(new IamTenantExecutionContext("INSTANCE",actor),()->{try{return invocation.proceed();}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException(failure);}});ProxyFactory factory=new ProxyFactory();factory.setTarget(target);factory.setInterfaces(api);factory.addAdvice(scope);factory.addAdvice(transaction);return api.cast(factory.getProxy(api.getClassLoader()));}
    private static <T>T proxy(T target,Class<T> api,PlatformTransactionManager manager){DefaultTransactionAttribute a=new DefaultTransactionAttribute();a.setName("iam-rbac:"+api.getSimpleName());MatchAlwaysTransactionAttributeSource source=new MatchAlwaysTransactionAttributeSource();source.setTransactionAttribute(a);TransactionInterceptor advice=new TransactionInterceptor(manager,source);ProxyFactory factory=new ProxyFactory();factory.setTarget(target);factory.setInterfaces(api);factory.addAdvice(advice);return api.cast(factory.getProxy(api.getClassLoader()));}
}
