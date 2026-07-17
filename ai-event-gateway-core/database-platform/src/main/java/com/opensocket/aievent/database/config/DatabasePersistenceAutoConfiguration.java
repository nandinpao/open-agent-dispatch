package com.opensocket.aievent.database.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.opensocket.aievent.database.persistence.DatabasePersistenceModule;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/** Registers only approved persistence adapters and converters. */
@AutoConfiguration
@AutoConfigureAfter(DatabasePlatformAutoConfiguration.class)
@ConditionalOnClass(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "aeg.database-platform", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(
        basePackageClasses = DatabasePersistenceModule.class,
        useDefaultFilters = false,
        includeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ANNOTATION,
                        classes = DatabaseRepositoryAdapter.class),
                @ComponentScan.Filter(
                        type = FilterType.ANNOTATION,
                        classes = DatabasePersistenceConverter.class)
        })
public class DatabasePersistenceAutoConfiguration {
}
