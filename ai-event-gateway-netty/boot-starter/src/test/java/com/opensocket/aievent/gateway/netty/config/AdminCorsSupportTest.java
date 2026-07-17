package com.opensocket.aievent.gateway.netty.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCorsSupportTest {

    @Test
    void shouldAllowExplicitReactAdminOrigin() {
        var properties = new AdminProperties();
        properties.setCorsAllowedOrigins(List.of("http://mydomain.com:3000"));

        var configuration = AdminCorsSupport.corsConfiguration(properties);

        assertThat(configuration.checkOrigin("http://mydomain.com:3000"))
                .isEqualTo("http://mydomain.com:3000");
    }

    @Test
    void shouldAllowReactAdminOriginByPattern() {
        var properties = new AdminProperties();
        properties.setCorsAllowedOrigins(List.of("http://localhost:3000"));
        properties.setCorsAllowedOriginPatterns(List.of("http://*.example.com:[3000,3001]"));

        var configuration = AdminCorsSupport.corsConfiguration(properties);

        assertThat(configuration.checkOrigin("http://admin.example.com:3000"))
                .isEqualTo("http://admin.example.com:3000");
    }

    @Test
    void shouldRejectUnexpectedOrigin() {
        var properties = new AdminProperties();
        properties.setCorsAllowedOrigins(List.of("http://mydomain.com:3000"));

        var configuration = AdminCorsSupport.corsConfiguration(properties);

        assertThat(configuration.checkOrigin("http://evil.example.com:3000")).isNull();
    }
}
