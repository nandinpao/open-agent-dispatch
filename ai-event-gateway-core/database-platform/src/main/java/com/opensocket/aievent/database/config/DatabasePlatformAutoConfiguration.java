package com.opensocket.aievent.database.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.opensocket.aievent.database.health.DatabasePlatformHealthIndicator;

import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnClass({DataSource.class, SqlSessionFactory.class, Flyway.class})
@ConditionalOnProperty(prefix = "aeg.database-platform", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DatabasePlatformProperties.class)
public class DatabasePlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DatabasePlatformRuntimeInspector databasePlatformRuntimeInspector(
            ObjectProvider<DataSource> dataSources,
            ObjectProvider<SqlSessionFactory> sqlSessionFactories,
            ObjectProvider<Flyway> flyways,
            DatabasePlatformProperties properties) {
        return new DatabasePlatformRuntimeInspector(dataSources, sqlSessionFactories, flyways, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    DatabasePlatformMybatisTypeHandlerRegistrar databasePlatformMybatisTypeHandlerRegistrar(
            ObjectProvider<SqlSessionFactory> sqlSessionFactories,
            ObjectMapper objectMapper) {
        return new DatabasePlatformMybatisTypeHandlerRegistrar(sqlSessionFactories, objectMapper);
    }

    @Bean(name = "aiEventGatewayDatabasePlatform")
    @ConditionalOnMissingBean(name = "aiEventGatewayDatabasePlatform")
    @ConditionalOnProperty(prefix = "aeg.database-platform", name = "health-enabled", havingValue = "true", matchIfMissing = true)
    DatabasePlatformHealthIndicator databasePlatformHealthIndicator(DatabasePlatformRuntimeInspector inspector,
                                                                    DatabasePlatformProperties properties) {
        return new DatabasePlatformHealthIndicator(inspector, properties);
    }
}
