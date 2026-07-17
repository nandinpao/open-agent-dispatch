package com.opensocket.aievent.database.config;

import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import com.opensocket.aievent.database.mybatis.typehandler.JsonMapTypeHandler;

import tools.jackson.databind.ObjectMapper;

public class DatabasePlatformMybatisTypeHandlerRegistrar implements SmartInitializingSingleton {
    private final ObjectProvider<SqlSessionFactory> sqlSessionFactories;
    private final ObjectMapper objectMapper;

    public DatabasePlatformMybatisTypeHandlerRegistrar(ObjectProvider<SqlSessionFactory> sqlSessionFactories,
                                                       ObjectMapper objectMapper) {
        this.sqlSessionFactories = sqlSessionFactories;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterSingletonsInstantiated() {
        sqlSessionFactories.orderedStream().forEach(factory ->
                factory.getConfiguration()
                        .getTypeHandlerRegistry()
                        .register(Map.class, JdbcType.OTHER, new JsonMapTypeHandler(objectMapper)));
    }
}
