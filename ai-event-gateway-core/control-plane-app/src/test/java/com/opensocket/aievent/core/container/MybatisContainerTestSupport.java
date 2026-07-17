package com.opensocket.aievent.core.container;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Builds thread-safe MyBatis mapper proxies against the same DataSource used by container tests. */
final class MybatisContainerTestSupport {
    private final SqlSessionTemplate sqlSessionTemplate;

    MybatisContainerTestSupport(DataSource dataSource) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] mapperLocations = resolver.getResources("classpath*:mybatis/postgresql/**/*.xml");
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setMapperLocations(mapperLocations);
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("MyBatis SqlSessionFactory was not created");
            }
            this.sqlSessionTemplate = new SqlSessionTemplate(factory);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize MyBatis container-test support", exception);
        }
    }

    <T> T mapper(Class<T> mapperType) {
        return sqlSessionTemplate.getMapper(mapperType);
    }
}
