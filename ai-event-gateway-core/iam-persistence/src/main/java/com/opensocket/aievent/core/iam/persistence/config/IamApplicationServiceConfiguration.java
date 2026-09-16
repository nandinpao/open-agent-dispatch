package com.opensocket.aievent.core.iam.persistence.config;

import java.time.Clock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.opensocket.aievent.core.iam.identity.application.port.in.*;
import com.opensocket.aievent.core.iam.identity.application.port.out.*;
import com.opensocket.aievent.core.iam.identity.application.service.*;
import com.opensocket.aievent.core.iam.organization.application.port.in.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.*;
import com.opensocket.aievent.core.iam.organization.application.service.*;
import com.opensocket.aievent.core.iam.organization.domain.*;

@AutoConfiguration(after=IamPersistenceAutoConfiguration.class)
public class IamApplicationServiceConfiguration {
    @Bean @ConditionalOnMissingBean Clock iamClock(){return Clock.systemUTC();}
    @Bean @ConditionalOnMissingBean IdentityCommandPort identityCommandPort(HumanUserRepository u,RootIdentityRepository r,IdentityEventPublisher e,Clock c,PlatformTransactionManager tx){return proxy(new IdentityApplicationService(u,r,e,c),IdentityCommandPort.class,tx,false);}
    @Bean @ConditionalOnMissingBean IdentityQueryPort identityQueryPort(HumanUserRepository u,RootIdentityRepository r,PlatformTransactionManager tx){return proxy(new IdentityQueryService(u,r),IdentityQueryPort.class,tx,true);}
    @Bean @ConditionalOnMissingBean TenantCommandPort tenantCommandPort(TenantRepository t,TenantMembershipRepository m,OrganizationEventPublisher e,DepartmentRepository d,Clock c,PlatformTransactionManager tx){return proxy(new TenantApplicationService(t,m,e,d,c),TenantCommandPort.class,tx,false);}
    @Bean @ConditionalOnMissingBean DepartmentCommandPort departmentCommandPort(DepartmentRepository d,DepartmentHierarchyRepository h,DepartmentRevisionRepository r,DepartmentMembershipRepository m,TenantMembershipRepository tm,GroupRepository g,OrganizationSnapshotRepository s,OrganizationEventPublisher e,IamPersistenceProperties p,Clock c,PlatformTransactionManager tx){return proxy(new DepartmentApplicationService(d,h,r,m,tm,g,s,e,new DepartmentHierarchyPolicy(p.getDepartmentMaxDepth()),new OrganizationSnapshotFactory(),c),DepartmentCommandPort.class,tx,false);}
    @Bean @ConditionalOnMissingBean GroupCommandPort groupCommandPort(GroupRepository g,GroupMembershipRepository m,DepartmentRepository d,TenantMembershipRepository tm,OrganizationEventPublisher e,Clock c,PlatformTransactionManager tx){return proxy(new GroupApplicationService(g,m,d,tm,e,c),GroupCommandPort.class,tx,false);}
    @Bean @ConditionalOnMissingBean OrganizationQueryPort organizationQueryPort(TenantRepository t,TenantMembershipRepository tm,DepartmentRepository d,DepartmentMembershipRepository dm,GroupMembershipRepository gm,GroupRepository g,Clock c,PlatformTransactionManager tx){return proxy(new OrganizationQueryService(t,tm,d,dm,gm,g,c),OrganizationQueryPort.class,tx,true);}
    private static <T>T proxy(T target,Class<T> api,PlatformTransactionManager manager,boolean readOnly){DefaultTransactionAttribute a=new DefaultTransactionAttribute();a.setReadOnly(readOnly);a.setName("iam:"+api.getSimpleName());MatchAlwaysTransactionAttributeSource source=new MatchAlwaysTransactionAttributeSource();source.setTransactionAttribute(a);TransactionInterceptor advice=new TransactionInterceptor(manager,source);ProxyFactory factory=new ProxyFactory();factory.setTarget(target);factory.setInterfaces(api);factory.addAdvice(advice);return api.cast(factory.getProxy(api.getClassLoader()));}
}
