package com.opensocket.aievent.gateway.netty.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * High-priority CORS filter for the Admin REST API.
 *
 * <p>React Admin UI usually runs on a different port, for example
 * {@code http://mydomain.com:3000}, while the Admin API runs on
 * {@code http://mydomain.com:18080}. Browsers send an OPTIONS preflight request before login.
 * This filter handles that preflight before the lightweight Admin token filter so an allowed
 * origin is not rejected as an unauthenticated request.</p>
 *
 * <p>P3.6.1 also marks this filter as a component. The executable gateway application and the
 * reusable starter share the same base package, so component scanning is the most deterministic
 * way to ensure the CORS filter is active in local Jar/Docker runs. The starter auto-configuration
 * still has a {@code @ConditionalOnMissingBean} fallback for external consumers.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCorsSupport extends CorsFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminCorsSupport.class);

    public AdminCorsSupport(AdminProperties adminProperties) {
        super(corsConfigurationSource(adminProperties));
        var configuration = corsConfiguration(adminProperties);
        log.info("Admin CORS configured: allowedOrigins={}, allowedOriginPatterns={}, allowCredentials={}, maxAgeSeconds={}",
                configuration.getAllowedOrigins(),
                configuration.getAllowedOriginPatterns(),
                configuration.getAllowCredentials(),
                configuration.getMaxAge());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    static CorsConfigurationSource corsConfigurationSource(AdminProperties adminProperties) {
        var source = new UrlBasedCorsConfigurationSource();
        var configuration = corsConfiguration(adminProperties);
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/internal/**", configuration);
        return source;
    }

    static CorsConfiguration corsConfiguration(AdminProperties adminProperties) {
        var configuration = new CorsConfiguration();
        var origins = new LinkedHashSet<>(clean(adminProperties.corsAllowedOrigins()));
        var patterns = new LinkedHashSet<>(clean(adminProperties.corsAllowedOriginPatterns()));

        // Defensive fallback for Docker/.env deployments. If Spring property binding is correct,
        // these values are already present through AdminProperties. If an operator only set
        // GATEWAY_PUBLIC_HOST or ADMIN_UI_PUBLIC_ORIGIN in .env, derive the common Admin UI origin
        // so machine-admin API preflight is not rejected before authentication is evaluated.
        addCsv(origins, env("ADMIN_CORS_ALLOWED_ORIGINS"));
        addCsv(patterns, env("ADMIN_CORS_ALLOWED_ORIGIN_PATTERNS"));
        addCsv(patterns, env("EXTRA_ADMIN_CORS_ALLOWED_ORIGIN_PATTERNS"));
        addLocalDevPatternsWhenLocalProfileIsActive(patterns);
        addOrigin(origins, env("ADMIN_UI_PUBLIC_ORIGIN"));
        addReactOriginForHost(origins, env("GATEWAY_PUBLIC_HOST"));
        addReactOriginForHost(origins, env("HOST_IP"));

        if (!origins.isEmpty()) {
            configuration.setAllowedOrigins(List.copyOf(origins));
        }
        if (!patterns.isEmpty()) {
            configuration.setAllowedOriginPatterns(List.copyOf(patterns));
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Admin-Token"));
        configuration.setAllowCredentials(adminProperties.corsAllowCredentials());
        configuration.setMaxAge(adminProperties.corsMaxAgeSeconds());
        return configuration;
    }

    private static String env(String key) {
        var value = System.getenv(key);
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static void addCsv(LinkedHashSet<String> target, String csv) {
        if (!StringUtils.hasText(csv)) {
            return;
        }
        for (var item : csv.split(",")) {
            addOrigin(target, item);
        }
    }

    private static void addOrigin(LinkedHashSet<String> target, String origin) {
        if (StringUtils.hasText(origin)) {
            target.add(origin.trim());
        }
    }

    private static void addLocalDevPatternsWhenLocalProfileIsActive(LinkedHashSet<String> target) {
        var profiles = env("SPRING_PROFILES_ACTIVE");
        if (!StringUtils.hasText(profiles)) {
            return;
        }
        for (var profile : profiles.split(",")) {
            if ("local".equalsIgnoreCase(profile.trim())) {
                addOrigin(target, "http://*:[3000]");
                addOrigin(target, "http://*:[3001]");
                addOrigin(target, "https://*:[3000]");
                addOrigin(target, "https://*:[3001]");
                return;
            }
        }
    }

    private static void addReactOriginForHost(LinkedHashSet<String> target, String host) {
        if (!StringUtils.hasText(host)) {
            return;
        }
        var cleanHost = host.trim();
        if (cleanHost.startsWith("http://") || cleanHost.startsWith("https://")) {
            addOrigin(target, cleanHost.endsWith(":3000") ? cleanHost : cleanHost + ":3000");
            return;
        }
        addOrigin(target, "http://" + cleanHost + ":3000");
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }
}
