package com.opensocket.aievent.core.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextFilter;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import com.opensocket.aievent.core.iam.runtime.security.IamApiSecurityConfiguration;
import com.opensocket.aievent.core.iam.runtime.security.IamBrowserSessionAuthenticationFilter;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityApiContext;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityApiContextPort;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityController;
import com.opensocket.aievent.core.uicapability.api.UiPageBootstrapController;
import com.opensocket.aievent.core.uicapability.contract.UiCapability;
import com.opensocket.aievent.core.uicapability.contract.UiCapabilityContract;
import com.opensocket.aievent.core.uicapability.contract.UiCapabilityEnvelope;
import com.opensocket.aievent.core.uicapability.contract.UiDisplayMode;
import com.opensocket.aievent.core.uicapability.contract.UiPageBootstrap;
import com.opensocket.aievent.core.uicapability.contract.UiPageBootstrapOutcome;
import com.opensocket.aievent.core.uicapability.core.UiCapabilityProjectionService;
import com.opensocket.aievent.core.uicapability.core.UiPageBootstrapService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {UiCapabilityController.class, UiPageBootstrapController.class},
        properties = {
                "ui-capability.enabled=true",
                "ui-capability.projection-api-enabled=true",
                "ui-capability.page-bootstrap-enabled=true",
                "aeg.iam.runtime.enabled=true"
        })
@Import({IamApiSecurityConfiguration.class, Phase7AUiCapabilityHttpSecurityIntegrationTest.SecurityConfiguration.class})
class Phase7AUiCapabilityHttpSecurityIntegrationTest {
    @Autowired MockMvc mvc;

    @MockitoBean UiCapabilityProjectionService projection;
    @MockitoBean UiPageBootstrapService bootstraps;
    @MockitoBean UiCapabilityApiContextPort contexts;
    @MockitoBean IamBrowserSessionAuthenticationFilter iamBrowserSessionAuthenticationFilter;
    @MockitoBean R3HttpAuthorizationFilter r3AuthorizationFilter;
    @MockitoBean OpenDispatchRequestContextFilter requestContextFilter;

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityConfiguration {
        @Bean
        IamRuntimeProperties iamRuntimeProperties() {
            IamRuntimeProperties properties = new IamRuntimeProperties();
            properties.setEnabled(true);
            properties.setSessionCookiePath("/");
            return properties;
        }
    }

    @BeforeEach
    void configureRuntimeMocks() throws Exception {
        continueFilterChain(iamBrowserSessionAuthenticationFilter);
        continueFilterChain(requestContextFilter);
        continueFilterChain(r3AuthorizationFilter);

        AuthenticationContext authentication = org.mockito.Mockito.mock(AuthenticationContext.class);
        when(contexts.current()).thenReturn(new UiCapabilityApiContext(authentication, "phase7a-runtime-test", Instant.parse("2026-08-01T00:00:00Z")));
        when(projection.project(any())).thenReturn(envelope());
        when(bootstraps.bootstrap(any())).thenReturn(bootstrap());
    }

    @Test
    void anonymousRequestsAreRejectedBeforeCapabilityProjection() throws Exception {
        mvc.perform(get("/api/ui/bootstrap/task.detail")
                        .queryParam("resourceId", "task-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedViewerCanReadPageBootstrapAndResponseIsNotCacheable() throws Exception {
        mvc.perform(get("/api/ui/bootstrap/task.detail")
                        .with(user("viewer").roles("VIEWER"))
                        .queryParam("resourceId", "task-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Vary", org.hamcrest.Matchers.containsString("Cookie")))
                .andExpect(jsonPath("$.outcome").value("PAGE"));
    }

    @Test
    void capabilityBatchUsesHumanSessionAndRequiresCsrfButNotOperatorRole() throws Exception {
        String request = """
                {"contractVersion":"1.0","contexts":[{"contextId":"task.detail","resourceId":"task-001","resourceVersion":1,"presentedPrincipalEpoch":1,"uiActionIds":["task.detail.view"]}]}
                """;

        mvc.perform(post("/api/ui/capabilities:batch")
                        .with(user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/ui/capabilities:batch")
                        .with(user("viewer").roles("VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value(UiCapabilityContract.VERSION));
    }

    private static void continueFilterChain(Filter filter) throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(filter).doFilter(any(), any(), any());
    }

    private static UiCapabilityEnvelope envelope() {
        Instant refresh = Instant.parse("2026-08-01T00:00:10Z");
        Instant expires = Instant.parse("2026-08-01T00:01:00Z");
        UiCapability capability = new UiCapability(
                "task.detail.view", UiDisplayMode.ENABLED, null, false, false,
                VisibilityLevel.STANDARD, false, "ui.help.task.detail.view");
        return new UiCapabilityEnvelope(
                UiCapabilityContract.VERSION, "task.detail", "tenant-a", 1, 1, 1,
                "resource-hash", 1L, "FORMAL", List.of(capability), expires, refresh, "");
    }

    private static UiPageBootstrap bootstrap() {
        Instant expires = Instant.parse("2026-08-01T00:01:00Z");
        UiCapability capability = new UiCapability(
                "task.detail.view", UiDisplayMode.ENABLED, null, false, false,
                VisibilityLevel.STANDARD, false, "ui.help.task.detail.view");
        return new UiPageBootstrap(
                UiCapabilityContract.VERSION, "task.detail", "/tasks/task-001", "tenant-a",
                1, 1, 1, UiPageBootstrapOutcome.PAGE, List.of(), List.of(capability),
                "resource-hash", 1L, expires, "runtime-nonce");
    }
}
