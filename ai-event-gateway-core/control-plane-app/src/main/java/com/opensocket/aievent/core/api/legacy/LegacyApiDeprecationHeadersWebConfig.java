package com.opensocket.aievent.core.api.legacy;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers Phase 4-3 deprecation headers for legacy routing APIs.
 */
@Configuration
public class LegacyApiDeprecationHeadersWebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LegacyApiDeprecationHeadersInterceptor())
                .addPathPatterns(
                        "/admin/dispatch-contracts/**",
                        "/admin/dispatch-contract/**",
                        "/admin/dispatch-policies/**",
                        "/admin/dispatch-governance/cutover/**",
                        "/admin/agents/*/dispatch-eligibility",
                        "/admin/tasks/*/dispatch-requirements",
                        "/admin/tasks/*/eligible-agents",
                        "/admin/tasks/*/eligible-agents-v2",
                        "/admin/enforce/legacy-final-report"
                );
    }
}
