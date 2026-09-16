package com.opensocket.aievent.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ApplicationLocalYamlConfigurationTest {

    @Test
    void loadsTheLocalProfileWithoutDuplicateRootKeys() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-local", new ClassPathResource("application-local.yml"));

        assertThat(property(sources, "aeg.iam.persistence.department-max-depth")).isNotNull();
        assertThat(property(sources, "aeg.enforcement-activation.control-plane-enabled")).isNotNull();
        assertThat(property(sources, "aeg.enforcement-activation.revision-sync-enabled")).isNotNull();
        assertThat(property(sources, "aeg.enforcement-activation.wave0-read-pilot-enabled")).isNotNull();
        assertThat(property(sources, "aeg.enforcement-activation.control-plane-enabled").toString())
                .contains("AEG_ENFORCEMENT_ACTIVATION_CONTROL_PLANE_ENABLED");
        assertThat(property(sources, "aeg.enforcement-activation.revision-sync-enabled").toString())
                .contains("AEG_ENFORCEMENT_ACTIVATION_REVISION_SYNC_ENABLED");
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
