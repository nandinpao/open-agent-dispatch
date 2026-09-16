package com.opensocket.aievent.core.uiaccessrequest.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestResult;
import com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestService;
import com.opensocket.aievent.core.uicapability.api.UiAccessRequestController;
import com.opensocket.aievent.core.uicapability.api.UiAccessRequestExceptionHandler;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityApiContext;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityApiContextPort;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UiAccessRequestController.class, properties = {
        "ui-capability.enabled=true", "ui-capability.access-request-enabled=true"})
@Import(UiAccessRequestExceptionHandler.class)
class Phase7EAccessRequestHttpSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean GovernedAccessRequestService service;
    @MockitoBean UiCapabilityApiContextPort contexts;

    @BeforeEach
    void configure() {
        AuthenticationContext authentication = org.mockito.Mockito.mock(AuthenticationContext.class);
        when(authentication.principal()).thenReturn(new PrincipalRef(PrincipalRef.PrincipalType.USER, "reviewer"));
        when(contexts.current()).thenReturn(new UiCapabilityApiContext(authentication, "phase7e-access-request", Instant.parse("2026-08-01T12:00:00Z")));
        when(service.submit(any())).thenReturn(result(false));
        when(service.findForReviewer(any(), any(), any(), any())).thenReturn(result(false));
        when(service.approve(any())).thenReturn(activeResult());
        when(service.reject(any())).thenReturn(rejectedResult());
    }

    @Test
    void anonymousSubmissionIsRejected() throws Exception {
        mvc.perform(post("/api/ui/access-requests").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-anonymous")
                .content(submitBody())).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequesterNeedsCsrfButNotOperatorRole() throws Exception {
        mvc.perform(post("/api/ui/access-requests").with(user("requester").roles("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "idem-no-csrf")
                .content(submitBody())).andExpect(status().isForbidden());
        mvc.perform(post("/api/ui/access-requests").with(user("requester").roles("VIEWER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "idem-csrf")
                .content(submitBody())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.state").value("PENDING_APPROVAL"));
    }

    @Test
    @WithMockUser(authorities = "resource.scope.approve")
    void reviewerReadAndApprovalPreserveVersionsAndNoStore() throws Exception {
        mvc.perform(get("/api/ui/access-requests/request-1/review"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestVersion").value(2));
        mvc.perform(post("/api/ui/access-requests/request-1/approve").with(csrf())
                .header("If-Match", "2").header("X-Scope-Grant-Version", "2")
                .header("X-Resource-Version", "7").header("X-Audit-Reason", "Independent approval")
                .header("Idempotency-Key", "approve-idem"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    private static String submitBody() { return """
            {"uiActionId":"task.detail.update","resourceId":"task-1","expectedResourceVersion":7,
             "requestedVisibility":"STANDARD","durationHours":24,
             "businessPurpose":"Temporary production investigation and repair."}
            """; }

    private static GovernedAccessRequestResult result(boolean active) {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        GovernedAccessRequestState requestState = active ? GovernedAccessRequestState.ACTIVE : GovernedAccessRequestState.PENDING_APPROVAL;
        ScopeGrantState grantState = active ? ScopeGrantState.ACTIVE : ScopeGrantState.PENDING_APPROVAL;
        String approver = active ? "reviewer" : "";
        return new GovernedAccessRequestResult(
                new GovernedAccessRequestRecord("TENANT-A", "request-1", "grant-1", "task.detail.update",
                        ResourceType.TASK, "task-1", 7, VisibilityLevel.STANDARD, "requester",
                        "Temporary production investigation and repair.", now, now.plusSeconds(86400), requestState,
                        approver, "request-idem", active ? 3 : 2, now, now),
                new ScopeGrantRecord("TENANT-A", "grant-1", ScopePrincipalType.USER, "requester", "task.update",
                        ResourceType.TASK, ScopeType.RESOURCE, "task-1", VisibilityLevel.STANDARD, now,
                        now.plusSeconds(86400), ScopeGrantSource.ACCESS_REQUEST, "Temporary production investigation and repair.",
                        "requester", approver, grantState, "grant-idem", active ? 3 : 2, now, now));
    }
    private static GovernedAccessRequestResult activeResult() { return result(true); }
    private static GovernedAccessRequestResult rejectedResult() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        return new GovernedAccessRequestResult(
                new GovernedAccessRequestRecord("TENANT-A", "request-1", "grant-1", "task.detail.update", ResourceType.TASK,
                        "task-1", 7, VisibilityLevel.STANDARD, "requester", "Temporary production investigation and repair.",
                        now, now.plusSeconds(86400), GovernedAccessRequestState.REJECTED, "reviewer", "request-idem", 3, now, now),
                new ScopeGrantRecord("TENANT-A", "grant-1", ScopePrincipalType.USER, "requester", "task.update", ResourceType.TASK,
                        ScopeType.RESOURCE, "task-1", VisibilityLevel.STANDARD, now, now.plusSeconds(86400), ScopeGrantSource.ACCESS_REQUEST,
                        "Temporary production investigation and repair.", "requester", "reviewer", ScopeGrantState.REVOKED, "grant-idem", 3, now, now));
    }
}
