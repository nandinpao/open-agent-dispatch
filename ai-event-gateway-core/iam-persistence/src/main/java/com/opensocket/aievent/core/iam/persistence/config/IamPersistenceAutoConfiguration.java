package com.opensocket.aievent.core.iam.persistence.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import javax.sql.DataSource;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.opensocket.aievent.core.iam.persistence.tenant.TenantTransactionMybatisInterceptor;
import com.opensocket.aievent.core.iam.persistence.transaction.SpringTenantRbacExecutionAdapter;
import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import org.springframework.transaction.PlatformTransactionManager;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@AutoConfiguration
@ConditionalOnClass(SqlSessionFactory.class)
@EnableConfigurationProperties(IamPersistenceProperties.class)
@ComponentScan(basePackages="com.opensocket.aievent.core.iam.persistence", useDefaultFilters=false,
    includeFilters=@ComponentScan.Filter(type=FilterType.ANNOTATION, classes=DatabaseRepositoryAdapter.class))
public class IamPersistenceAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    TenantTransactionMybatisInterceptor tenantTransactionMybatisInterceptor() {
        return new TenantTransactionMybatisInterceptor();
    }
    @Bean
    SmartInitializingSingleton registerIamTenantInterceptor(
            java.util.List<SqlSessionFactory> factories,
            TenantTransactionMybatisInterceptor interceptor) {
        return () -> factories.forEach(factory -> {
            if (factory.getConfiguration().getInterceptors().stream().noneMatch(i -> i == interceptor)) {
                factory.getConfiguration().addInterceptor(interceptor);
            }
        });
    }
    @Bean @ConditionalOnMissingBean
    TenantRbacExecutionPort tenantRbacExecutionPort(PlatformTransactionManager manager) {
        return new SpringTenantRbacExecutionAdapter(manager);
    }

    @Bean
    IamDatabaseRoleValidator iamDatabaseRoleValidator(DataSource dataSource, IamPersistenceProperties properties) {
        return new IamDatabaseRoleValidator(dataSource, properties);
    }

}
