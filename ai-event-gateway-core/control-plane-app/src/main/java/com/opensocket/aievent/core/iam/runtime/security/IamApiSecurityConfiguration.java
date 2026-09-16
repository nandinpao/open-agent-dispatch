package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextFilter;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import com.opensocket.aievent.core.security.R3HttpAuthorizationFilter;
import com.opensocket.aievent.core.security.OpenDispatchCsrfSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/** Single canonical security chain for every Human Admin /api entry point. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aeg.iam.runtime", name = "enabled", havingValue = "true")
public class IamApiSecurityConfiguration {
    @Bean
    @Order(4)
    SecurityFilterChain iamApiSecurityFilterChain(
            HttpSecurity http,
            IamBrowserSessionAuthenticationFilter authenticationFilter,
            A2AAgentMachineAuthenticationFilter agentA2AFilter,
            IamPersonalAccessTokenAuthenticationFilter personalAccessTokenFilter,
            R3HttpAuthorizationFilter authorizationFilter,
            OpenDispatchRequestContextFilter requestContextFilter,
            IamRuntimeProperties properties) throws Exception {
        var csrf = OpenDispatchCsrfSupport.cookieRepository(properties.getSessionCookiePath());

        http.securityMatcher("/api/**")
                .csrf(configurer -> configurer
                        .csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(OpenDispatchCsrfSupport.requestHandler())
                        .ignoringRequestMatchers(
                                IamPersonalAccessTokenAuthenticationFilter::hasPatBearer,
                                A2AAgentMachineAuthenticationFilter::hasAgentA2ABearer))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(agentA2AFilter, IamBrowserSessionAuthenticationFilter.class)
                .addFilterAfter(personalAccessTokenFilter, IamBrowserSessionAuthenticationFilter.class)
                .addFilterAfter(requestContextFilter, IamPersonalAccessTokenAuthenticationFilter.class)
                .addFilterAfter(authorizationFilter, OpenDispatchRequestContextFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(request, response, 401, "AUTHENTICATION_REQUIRED", "IAM authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeAccessDenied(request, response, exception)));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/bootstrap/status",
                        "/api/session/csrf",
                        "/api/session/federation/providers",
                        "/api/session/federation/oidc/callback",
                        "/api/platform/runtime-capabilities").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/session/login",
                        "/api/session/federation/oidc/start",
                        "/api/session/mfa/verify",
                        "/api/session/forgot-password",
                        "/api/session/reset-password",
                        "/api/session/activate-invitation").permitAll()
                .requestMatchers("/api/bootstrap/**").authenticated()
                .requestMatchers("/api/session", "/api/session/**").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll());
        return http.build();
    }

    private static void writeAccessDenied(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        if (exception instanceof MissingCsrfTokenException) {
            writeError(request, response, 403, "AUTH_CSRF_TOKEN_MISSING",
                    "The CSRF token is missing. Refresh the security token and retry the request.");
            return;
        }
        if (exception instanceof InvalidCsrfTokenException) {
            writeError(request, response, 403, "AUTH_CSRF_TOKEN_INVALID",
                    "The CSRF token is invalid or expired. Refresh the security token and retry the request.");
            return;
        }
        writeError(request, response, 403, "AUTH_PERMISSION_DENIED", "IAM permission is denied.");
    }

    private static void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        String correlationId = correlationId(request);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-Id", correlationId);
        response.getWriter().write(
                "{\"code\":\"" + escape(code) + "\","
                        + "\"error_code\":\"" + escape(code) + "\","
                        + "\"message\":\"" + escape(message) + "\","
                        + "\"correlationId\":\"" + escape(correlationId) + "\"}");
    }

    private static String correlationId(HttpServletRequest request) {
        Object existing = request.getAttribute("opendispatch.correlationId");
        if (existing instanceof String value && !value.isBlank()) return value;
        String value = request.getHeader("X-Correlation-Id");
        if (value == null || value.isBlank()) value = request.getHeader("X-Request-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
