package com.opensocket.aievent.core.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class AdminSessionRedisConfigurationTest {

    @Test
    void usesBoot4IndexedRedisSessionRepositoryForSessionInventoryAndRevocation() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(property(sources, "spring.session.data.redis.repository-type")).isEqualTo("indexed");
        assertThat(property(sources, "spring.session.data.redis.namespace")).isNotNull();
        assertThat(property(sources, "spring.session.redis.namespace")).isNull();
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
