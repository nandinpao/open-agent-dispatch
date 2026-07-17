package com.opensocket.aievent.core.security;

import java.io.IOException;
import java.util.Arrays;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextFilter;
import com.opensocket.aievent.core.identity.AdminIdentityProperties;
import com.opensocket.aievent.core.identity.AdminUserDetailsService;

@Configuration
public class CoreInternalSecurityConfiguration {
    static final String ADAPTER_ACTION_RECOVER_EXPIRED_LEASE_PATTERN =
            "/internal/adapter-actions/*/recover-expired-lease";
    static final String AGENT_AUTHORIZE_CONNECTION_PATTERN =
            "/internal/agents/authorize-connection";
    static final String AGENT_SECURITY_EVENTS_PATTERN =
            "/internal/agents/security-events";
    static final String AGENT_ENROLLMENTS_PATTERN =
            "/internal/agents/enrollments";

    static final String[] STANDARD_ADMIN_WORKFLOW_PATHS = {
            "/admin/source-systems/**",
            "/admin/agent-enrollments/**",
            "/admin/agents/**",
            "/admin/dispatch-flows/**",
            "/admin/tasks/**",
            "/admin/dispatch-requests/**"
    };

    static final String[] STANDARD_ADMIN_READ_ROLES = {
            "VIEWER",
            "OPERATOR",
            "ADMIN"
    };

    static final String[] STANDARD_ADMIN_MUTATION_ROLES = {
            "OPERATOR",
            "ADMIN"
    };

    private static final String[] LEGACY_SUPPORT_PATHS = {
            "/admin/assignment-profiles/**",
            "/admin/dispatch-task-definitions/**",
            "/admin/supply-profiles/**",
            "/admin/dispatch-policies/**",
            "/admin/dispatch-contracts/**",
            "/admin/agent-skills/**",
            "/admin/agents/*/skills/**",
            "/admin/agents/*/qualifications/**",
            "/admin/agents/*/supply-profiles/**"
    };

    @Bean
    public CoreInternalSecurityRequestClassifier coreInternalSecurityRequestClassifier(CoreInternalSecurityProperties properties) {
        return new CoreInternalSecurityRequestClassifier(properties);
    }

    @Bean
    public CoreInternalTokenVerifier coreInternalTokenVerifier(CoreInternalSecurityProperties properties,
                                                               CoreInternalSecurityRequestClassifier classifier) {
        return new CoreInternalTokenVerifier(properties, classifier);
    }

    @Bean
    public CoreInternalTokenAuthenticationFilter coreInternalTokenAuthenticationFilter(CoreInternalSecurityProperties properties,
                                                                                       CoreInternalTokenVerifier verifier) {
        return new CoreInternalTokenAuthenticationFilter(properties, verifier);
    }

    @Bean
    public PasswordEncoder coreAdminPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager coreAdminAuthenticationManager(AdminUserDetailsService userDetailsService,
                                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository coreAdminSecurityContextRepository() {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }

    @Bean
    public SessionAuthenticationStrategy coreAdminSessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain coreInternalMachineSecurityFilterChain(HttpSecurity http,
                                                                      CoreInternalSecurityProperties properties,
                                                                      CoreInternalTokenAuthenticationFilter filter,
                                                                      OpenDispatchRequestContextFilter requestContextFilter) throws Exception {
        http.securityMatcher("/internal/**", "/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestContextFilter, CoreInternalTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(response, 401, "UNAUTHORIZED", "Internal authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(response, 403, "FORBIDDEN", "Internal role is not allowed.")));

        if (!properties.isEnabled()) {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        http.authorizeHttpRequests(auth -> {
            if (properties.isPermitActuatorHealthInfo()) {
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
            }
            auth.requestMatchers("/actuator/**").hasRole(CoreInternalSecurityRole.ACTUATOR.name())
                    .requestMatchers("/internal/gateway-nodes/**").hasRole(CoreInternalSecurityRole.GATEWAY.name())
                    .requestMatchers(AGENT_AUTHORIZE_CONNECTION_PATTERN, AGENT_SECURITY_EVENTS_PATTERN, AGENT_ENROLLMENTS_PATTERN).hasRole(CoreInternalSecurityRole.GATEWAY.name())
                    .requestMatchers(HttpMethod.POST, "/internal/control-plane/tasks/*/ack", "/internal/control-plane/tasks/*/progress", "/internal/control-plane/tasks/*/result", "/internal/control-plane/tasks/*/error").hasRole(CoreInternalSecurityRole.GATEWAY.name())
                    .requestMatchers("/internal/control-plane/tasks/**").hasRole(CoreInternalSecurityRole.OPERATOR.name())
                    .requestMatchers("/internal/adapter-actions/*/heartbeat", "/internal/adapter-actions/*/complete", "/internal/adapter-actions/*/fail", "/internal/adapter-actions/claim").hasRole(CoreInternalSecurityRole.ADAPTER_WORKER.name())
                    .requestMatchers(ADAPTER_ACTION_RECOVER_EXPIRED_LEASE_PATTERN, "/internal/adapter-actions/recover-expired-leases").hasRole(CoreInternalSecurityRole.OPERATOR.name())
                    .requestMatchers("/internal/**").hasRole(CoreInternalSecurityRole.OPERATOR.name())
                    .anyRequest().denyAll();
        });
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain coreEventIntakeSecurityFilterChain(HttpSecurity http,
                                                                  CoreInternalSecurityProperties properties,
                                                                  CoreInternalTokenAuthenticationFilter filter,
                                                                  OpenDispatchRequestContextFilter requestContextFilter) throws Exception {
        http.securityMatcher("/api/events/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestContextFilter, CoreInternalTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(response, 401, "UNAUTHORIZED", "Event intake authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(response, 403, "FORBIDDEN", "Event intake credential is not allowed.")));

        if (!properties.isEnabled()) {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
        return http.authorizeHttpRequests(auth -> auth.anyRequest()
                        .hasRole(CoreInternalSecurityRole.EVENT_INGESTION.name()))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain coreHumanAdminSecurityFilterChain(HttpSecurity http,
                                                                 CoreInternalSecurityProperties internalProperties,
                                                                 AdminIdentityProperties adminProperties,
                                                                 CoreInternalTokenAuthenticationFilter internalTokenFilter,
                                                                 CoreInternalTokenVerifier internalTokenVerifier,
                                                                 OpenDispatchRequestContextFilter requestContextFilter,
                                                                 SecurityContextRepository securityContextRepository) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        RequestMatcher validInternalToken = internalTokenVerifier::isValidInternalTokenRequest;

        http.securityMatcher("/api/auth/**", "/admin/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers(validInternalToken))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestContextFilter, CoreInternalTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(response, 401, "UNAUTHORIZED", "Admin authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(response, 403, "FORBIDDEN", adminAccessDeniedMessage(request))));

        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/security-audit").hasRole("ADMIN")
                    .requestMatchers("/api/auth/**").authenticated();

            if (!internalProperties.isEnabled() && !adminProperties.isEnabled()) {
                auth.requestMatchers("/admin/**").permitAll();
            } else {
                auth.requestMatchers(HttpMethod.GET, LEGACY_SUPPORT_PATHS)
                        .hasRole("SUPPORT")
                        .requestMatchers(HttpMethod.HEAD, LEGACY_SUPPORT_PATHS)
                        .hasRole("SUPPORT")
                        .requestMatchers(LEGACY_SUPPORT_PATHS)
                        .denyAll()
                        .requestMatchers(HttpMethod.GET, STANDARD_ADMIN_WORKFLOW_PATHS)
                        .hasAnyRole(distinctRoles(STANDARD_ADMIN_READ_ROLES))
                        .requestMatchers(HttpMethod.HEAD, STANDARD_ADMIN_WORKFLOW_PATHS)
                        .hasAnyRole(distinctRoles(STANDARD_ADMIN_READ_ROLES))
                        .requestMatchers(STANDARD_ADMIN_WORKFLOW_PATHS)
                        .hasAnyRole(distinctRoles(STANDARD_ADMIN_MUTATION_ROLES))
                        .requestMatchers(HttpMethod.PUT, "/admin/dispatch-governance/cutover/policies/*")
                        .hasAnyRole(distinctRoles("ADMIN", CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.POST, "/admin/dispatch-governance/cutover/policies/*/rollback")
                        .hasAnyRole(distinctRoles("ADMIN", CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.POST,
                                "/admin/dispatch-governance/actions/grants/*/approve",
                                "/admin/dispatch-governance/actions/approval-requests/*/decide")
                        .hasAnyRole(distinctRoles("ADMIN", CoreInternalSecurityRole.RECOVERY_APPROVER.name()))
                        .requestMatchers(HttpMethod.PUT,
                                "/admin/dispatch-governance/actions/catalog/*",
                                "/admin/dispatch-governance/actions/grants/*")
                        .hasAnyRole(distinctRoles("ADMIN", CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.POST,
                                "/admin/dispatch-governance/actions/grants/*/revoke",
                                "/admin/dispatch-governance/actions/manual-cases/*/resolve")
                        .hasAnyRole(distinctRoles("ADMIN", CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.POST, "/admin/dispatch-governance/actions/**")
                        .hasAnyRole(distinctRoles("OPERATOR", "ADMIN", CoreInternalSecurityRole.RECOVERY_OPERATOR.name(), CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.POST,
                                "/admin/recovery/approval-requests/*/approve",
                                "/admin/recovery/approval-requests/*/reject")
                        .hasAnyRole(distinctRoles("ADMIN", CoreInternalSecurityRole.RECOVERY_APPROVER.name()))
                        .requestMatchers(HttpMethod.POST, "/admin/recovery/approval-requests/*/cancel")
                        .hasAnyRole(distinctRoles("OPERATOR", "ADMIN", CoreInternalSecurityRole.RECOVERY_OPERATOR.name(), CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.POST,
                                "/admin/recovery/actions/tasks/*/dead-letter",
                                "/admin/recovery/actions/tasks/*/restore-dead-letter",
                                "/admin/recovery/actions/dispatch-requests/*/dead-letter",
                                "/admin/recovery/actions/dispatch-requests/*/restore-dead-letter")
                        .hasAnyRole(distinctRoles("ADMIN", CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.POST, "/admin/recovery/actions/**")
                        .hasAnyRole(distinctRoles("OPERATOR", "ADMIN", CoreInternalSecurityRole.RECOVERY_OPERATOR.name(), CoreInternalSecurityRole.RECOVERY_ADMIN.name()))
                        .requestMatchers(HttpMethod.GET, "/admin/**")
                        .hasAnyRole(distinctRoles(STANDARD_ADMIN_READ_ROLES))
                        .requestMatchers(HttpMethod.HEAD, "/admin/**")
                        .hasAnyRole(distinctRoles(STANDARD_ADMIN_READ_ROLES))
                        .requestMatchers("/admin/**")
                        .hasAnyRole(distinctRoles(STANDARD_ADMIN_MUTATION_ROLES));
            }
            auth.anyRequest().denyAll();
        });
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain coreFallbackSecurityFilterChain(HttpSecurity http,
                                                               CoreInternalSecurityProperties properties,
                                                               CoreInternalTokenAuthenticationFilter filter,
                                                               OpenDispatchRequestContextFilter requestContextFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestContextFilter, CoreInternalTokenAuthenticationFilter.class);

        http.authorizeHttpRequests(auth -> {
            if (properties.isEnabled() && properties.isProtectApiMutations()) {
                auth.requestMatchers(HttpMethod.POST, "/api/**")
                        .hasRole(CoreInternalSecurityRole.OPERATOR.name())
                        .requestMatchers(HttpMethod.PUT, "/api/**")
                        .hasRole(CoreInternalSecurityRole.OPERATOR.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/**")
                        .hasRole(CoreInternalSecurityRole.OPERATOR.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/**")
                        .hasRole(CoreInternalSecurityRole.OPERATOR.name());
            }
            auth.anyRequest().permitAll();
        });
        return http.build();
    }

    static String[] distinctRoles(String... roles) {
        return Arrays.stream(roles).distinct().toArray(String[]::new);
    }

    private static String adminAccessDeniedMessage(jakarta.servlet.http.HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        if (matchesAny(path, STANDARD_ADMIN_WORKFLOW_PATHS)) {
            return "Standard Admin workflow access denied. VIEWER may read; OPERATOR and ADMIN may operate Source Systems, Agents, Dispatch Flows, and Tasks.";
        }
        if (matchesAny(path, LEGACY_SUPPORT_PATHS)) {
            return "This legacy or support-only endpoint is not part of the standard Dispatch Flow workflow.";
        }
        return "Admin access denied for this endpoint.";
    }

    private static boolean matchesAny(String path, String[] patterns) {
        if (path == null || patterns == null) return false;
        return Arrays.stream(patterns).anyMatch(pattern -> matchesAntPattern(path, pattern));
    }

    private static boolean matchesAntPattern(String path, String pattern) {
        if (pattern == null) return false;
        String normalizedPattern = pattern.endsWith("/**") ? pattern.substring(0, pattern.length() - 3) : pattern;
        if (pattern.endsWith("/**")) return path.equals(normalizedPattern) || path.startsWith(normalizedPattern + "/");
        return path.equals(pattern);
    }

    private static void writeSecurityError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
