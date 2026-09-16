package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof that opaque PUSH authentication resolves tenant only after handle+Bearer validation. */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class PCStage4PushCallbackRouteContainerTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_pc_s4_push").withUsername("opendispatch").withPassword("opendispatch");
    private JdbcTemplate jdbc; private A2APushCallbackRouteService routes;
    @BeforeEach void setUp(){DataSource ds=dataSource();jdbc=new JdbcTemplate(ds);routes=new A2APushCallbackRouteService(new NamedParameterJdbcTemplate(ds));jdbc.execute("drop table if exists a2a_push_callback_routes");jdbc.execute("create table a2a_push_callback_routes(callback_handle text primary key,tenant_id text not null,tracking_id text not null,token_hash text not null,route_status text not null default 'ACTIVE',expires_at timestamptz,request_window_started_at timestamptz,request_count int not null default 0,auth_failure_count int not null default 0,locked_until timestamptz,created_at timestamptz not null default now(),updated_at timestamptz not null default now())");}

    @Test void validOpaqueRouteResolvesTenantAfterBearerAuthentication(){String token="secret-token";routes.register("tenant-a","track-a","opaque-handle",sha(token));var route=routes.authenticate("opaque-handle",token);assertThat(route.tenantId()).isEqualTo("tenant-a");assertThat(route.trackingId()).isEqualTo("track-a");}
    @Test void wrongBearerNeverReturnsTenantRoute(){routes.register("tenant-a","track-a","opaque-handle",sha("right"));assertThrows(IllegalArgumentException.class,()->routes.authenticate("opaque-handle","wrong"));}
    @Test void repeatedAuthFailuresLockTheOpaqueHandle(){routes.register("tenant-a","track-a","opaque-handle",sha("right"));for(int i=0;i<10;i++)assertThrows(IllegalArgumentException.class,()->routes.authenticate("opaque-handle","wrong"));IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->routes.authenticate("opaque-handle","right"));assertThat(ex.getMessage()).isEqualTo("A2A_PUSH_RATE_LIMITED");}

    private static String sha(String v){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private DataSource dataSource(){DriverManagerDataSource ds=new DriverManagerDataSource();ds.setDriverClassName(POSTGRES.getDriverClassName());ds.setUrl(POSTGRES.getJdbcUrl());ds.setUsername(POSTGRES.getUsername());ds.setPassword(POSTGRES.getPassword());return ds;}
}
