package com.opensocket.aievent.gateway.netty.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration properties holder for Admin Web Mvc Config. Values are bound from application.yml
 * and environment variables so local, Docker, cluster, and production deployments can use the
 * same code path.
 */
@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminProperties adminProperties;

    public AdminWebMvcConfig(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var registration = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "X-Admin-Token")
                .allowCredentials(adminProperties.corsAllowCredentials())
                .maxAge(adminProperties.corsMaxAgeSeconds());

        if (!adminProperties.corsAllowedOrigins().isEmpty()) {
            registration.allowedOrigins(adminProperties.corsAllowedOrigins().toArray(String[]::new));
        }
        if (!adminProperties.corsAllowedOriginPatterns().isEmpty()) {
            registration.allowedOriginPatterns(adminProperties.corsAllowedOriginPatterns().toArray(String[]::new));
        }
    }
}
