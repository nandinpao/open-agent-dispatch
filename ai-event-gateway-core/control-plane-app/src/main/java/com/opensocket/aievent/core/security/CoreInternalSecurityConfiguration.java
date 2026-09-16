package com.opensocket.aievent.core.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.opensocket.aievent.core.iam.runtime.config.EventIntakeSecurityProperties;
import com.opensocket.aievent.core.iam.runtime.eventintake.EventIntakeMachineAuthenticationFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextFilter;
import com.opensocket.aievent.core.iam.runtime.security.IamBrowserSessionAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(EventIntakeSecurityProperties.class)
public class CoreInternalSecurityConfiguration {
    static final String ADAPTER_ACTION_RECOVER_EXPIRED_LEASE_PATTERN =
            "/internal/adapter-actions/*/recover-expired-lease";
    static final String AGENT_AUTHORIZE_CONNECTION_PATTERN =
            "/internal/agents/authorize-connection";
    static final String AGENT_SECURITY_EVENTS_PATTERN =
            "/internal/agents/security-events";
    static final String AGENT_ENROLLMENTS_PATTERN =
            "/internal/agents/enrollments";


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

    /**
     * Security-chain managed filters must not also be auto-registered as container-wide servlet filters.
     * Otherwise non-human routes can execute the filter outside their matching SecurityFilterChain.
     */
    @Bean
    public FilterRegistrationBean<CoreInternalTokenAuthenticationFilter> disableCoreInternalTokenAuthenticationFilterServletRegistration(
            CoreInternalTokenAuthenticationFilter filter) {
        FilterRegistrationBean<CoreInternalTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder legacyCredentialPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * PC-S4 remote A2A PUSH callback. The HTTP route remains public by protocol necessity,
     * but an opaque callback handle plus Bearer token is authenticated by A2APushCallbackRouteService
     * before any tenant context is bound. A2APushBodyLimitFilter caps bytes before deserialization.
     * No other /internal route is opened by this chain.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain a2aPushWebhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/a2a/push/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/internal/a2a/push/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
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
            auth.requestMatchers("/actuator/**").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.ACTUATOR))
                    .requestMatchers("/internal/gateway-nodes/**").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.GATEWAY))
                    .requestMatchers(AGENT_AUTHORIZE_CONNECTION_PATTERN, AGENT_SECURITY_EVENTS_PATTERN, AGENT_ENROLLMENTS_PATTERN).hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.GATEWAY))
                    .requestMatchers(HttpMethod.POST, "/internal/control-plane/tasks/*/ack", "/internal/control-plane/tasks/*/progress", "/internal/control-plane/tasks/*/result", "/internal/control-plane/tasks/*/error", "/internal/control-plane/tasks/*/capability-delegations").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.GATEWAY))
                    .requestMatchers(HttpMethod.GET, "/internal/control-plane/tasks/*/capability-delegations/*/closure-evidence").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.GATEWAY))
                    .requestMatchers("/internal/control-plane/tasks/**").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.OPERATOR))
                    .requestMatchers("/internal/adapter-actions/*/heartbeat", "/internal/adapter-actions/*/complete", "/internal/adapter-actions/*/fail", "/internal/adapter-actions/claim").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.ADAPTER_WORKER))
                    .requestMatchers(ADAPTER_ACTION_RECOVER_EXPIRED_LEASE_PATTERN, "/internal/adapter-actions/recover-expired-leases").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.OPERATOR))
                    .requestMatchers("/internal/**").hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.OPERATOR))
                    .anyRequest().denyAll();
        });
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain coreEventIntakeSecurityFilterChain(HttpSecurity http,
                                                                  CoreInternalSecurityProperties properties,
                                                                  CoreInternalTokenAuthenticationFilter filter,
                                                                  OpenDispatchRequestContextFilter requestContextFilter,
                                                                  EventIntakeSecurityProperties eventIntakeSecurityProperties,
                                                                  ObjectProvider<EventIntakeMachineAuthenticationFilter> machineFilterProvider) throws Exception {
        eventIntakeSecurityProperties.validate();
        EventIntakeMachineAuthenticationFilter machineFilter = machineFilterProvider.getIfAvailable();
        if (eventIntakeSecurityProperties.acceptsJwt() && machineFilter == null) {
            throw new IllegalStateException("Phase 9 JWT Event Intake modes require aeg.iam.machine-token.enabled=true");
        }
        if ((eventIntakeSecurityProperties.getMode() == EventIntakeSecurityProperties.Mode.SHADOW
                || eventIntakeSecurityProperties.getMode() == EventIntakeSecurityProperties.Mode.DUAL_ACCEPT)
                && !properties.isEnabled()) {
            throw new IllegalStateException("Phase 9 SHADOW/DUAL_ACCEPT requires core.security.internal.enabled=true for legacy compatibility");
        }

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

        if (machineFilter != null && eventIntakeSecurityProperties.acceptsJwt()) {
            http.addFilterBefore(machineFilter, CoreInternalTokenAuthenticationFilter.class);
        }

        if (!properties.isEnabled() && eventIntakeSecurityProperties.getMode() == EventIntakeSecurityProperties.Mode.LEGACY_ONLY) {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/events/intake").authenticated()
                        .anyRequest().hasAuthority(CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.EVENT_INGESTION)))
                .build();
    }

    @Bean
    @Order(5)
    public SecurityFilterChain coreHumanAdminSecurityFilterChain(
            HttpSecurity http,
            CoreInternalTokenAuthenticationFilter internalTokenFilter,
            CoreInternalTokenVerifier internalTokenVerifier,
            OpenDispatchRequestContextFilter requestContextFilter,
            ObjectProvider<IamBrowserSessionAuthenticationFilter> iamBrowserSessionAuthenticationFilter,
            ObjectProvider<R3HttpAuthorizationFilter> r3AuthorizationFilter) throws Exception {
        var csrfRepository = OpenDispatchCsrfSupport.cookieRepository("/");
        RequestMatcher validInternalToken = internalTokenVerifier::isValidInternalTokenRequest;

        http.securityMatcher("/admin/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(OpenDispatchCsrfSupport.requestHandler())
                        .ignoringRequestMatchers(validInternalToken))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context
                        .securityContextRepository(new NullSecurityContextRepository())
                        .requireExplicitSave(true))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestContextFilter, CoreInternalTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(
                                response, 401, "UNAUTHORIZED", "Canonical IAM authentication is required."))
                        .accessDeniedHandler(CoreInternalSecurityConfiguration::writeHumanAdminAccessDenied));

        IamBrowserSessionAuthenticationFilter iamSessionFilter = iamBrowserSessionAuthenticationFilter.getIfAvailable();
        if (iamSessionFilter != null) {
            http.addFilterBefore(iamSessionFilter, CoreInternalTokenAuthenticationFilter.class);
        }
        R3HttpAuthorizationFilter atomicPermissionFilter = r3AuthorizationFilter.getIfAvailable();
        if (atomicPermissionFilter == null) {
            throw new IllegalStateException("R3_HTTP_AUTHORIZATION_FILTER_REQUIRED");
        }
        http.addFilterAfter(atomicPermissionFilter, OpenDispatchRequestContextFilter.class);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/admin/**").authenticated()
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(6)
    public SecurityFilterChain coreFallbackSecurityFilterChain(HttpSecurity http,
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

        // All /api/** Human Admin routes are owned by the canonical IAM chain at order 4.
        // This fallback must never reintroduce role-based authorization.
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    private static void writeHumanAdminAccessDenied(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        if (exception instanceof MissingCsrfTokenException) {
            writeSecurityError(response, 403, "AUTH_CSRF_TOKEN_MISSING",
                    "The CSRF token is missing. Refresh the security token and retry the request.");
            return;
        }
        if (exception instanceof InvalidCsrfTokenException) {
            writeSecurityError(response, 403, "AUTH_CSRF_TOKEN_INVALID",
                    "The CSRF token is invalid or expired. Refresh the security token and retry the request.");
            return;
        }
        writeSecurityError(response, 403, "AUTH_PERMISSION_DENIED",
                "Atomic Permission authorization denied.");
    }

    private static void writeSecurityError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
