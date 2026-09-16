package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.uiaccessrequest.application.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ui/access-requests")
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "access-request-enabled"}, havingValue = "true")
@ConditionalOnBean({UiAccessRequestApplicationService.class, UiCapabilityApiContextPort.class})
public final class UiAccessRequestController {
    private final UiAccessRequestApplicationService accessRequests;
    private final UiCapabilityApiContextPort contexts;
    public UiAccessRequestController(UiAccessRequestApplicationService accessRequests, UiCapabilityApiContextPort contexts) {
        this.accessRequests = accessRequests; this.contexts = contexts;
    }

    @PostMapping
    public ResponseEntity<UiAccessRequestResponse> submit(@RequestBody SubmitBody body,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UiCapabilityApiContext trusted = contexts.current();
        GovernedAccessRequestResult result = accessRequests.submit(new SubmitGovernedAccessRequestCommand(
                trusted.authentication(), body.uiActionId(), body.resourceId(), body.expectedResourceVersion(),
                body.requestedVisibility(), body.durationHours(), body.businessPurpose(), trusted.correlationId(),
                idempotencyKey, trusted.requestedAt()));
        return noStore(response(result, trusted));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<UiAccessRequestResponse> read(@PathVariable String requestId) {
        UiCapabilityApiContext trusted = contexts.current();
        GovernedAccessRequestResult result = accessRequests.findForRequester(trusted.authentication(), requestId);
        return noStore(response(result, trusted));
    }


    @GetMapping("/{requestId}/review")
    public ResponseEntity<UiAccessRequestResponse> readForReview(@PathVariable String requestId) {
        UiCapabilityApiContext trusted = contexts.current();
        GovernedAccessRequestResult result = accessRequests.findForReviewer(trusted.authentication(), requestId,
                trusted.correlationId(), trusted.requestedAt());
        return noStore(response(result, trusted));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<UiAccessRequestResponse> approve(@PathVariable String requestId,
            @RequestHeader("If-Match") String requestVersion,
            @RequestHeader("X-Scope-Grant-Version") long grantVersion,
            @RequestHeader("X-Resource-Version") long resourceVersion,
            @RequestHeader("X-Audit-Reason") String reason,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UiCapabilityApiContext trusted = contexts.current();
        GovernedAccessRequestResult result = accessRequests.approve(review(trusted, requestId, requestVersion,
                grantVersion, resourceVersion, reason, idempotencyKey));
        return noStore(response(result, trusted));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<UiAccessRequestResponse> reject(@PathVariable String requestId,
            @RequestHeader("If-Match") String requestVersion,
            @RequestHeader("X-Scope-Grant-Version") long grantVersion,
            @RequestHeader("X-Resource-Version") long resourceVersion,
            @RequestHeader("X-Audit-Reason") String reason,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UiCapabilityApiContext trusted = contexts.current();
        GovernedAccessRequestResult result = accessRequests.reject(review(trusted, requestId, requestVersion,
                grantVersion, resourceVersion, reason, idempotencyKey));
        return noStore(response(result, trusted));
    }


    private static UiAccessRequestResponse response(GovernedAccessRequestResult result, UiCapabilityApiContext trusted) {
        return UiAccessRequestResponse.from(result.request(), result.grant(),
                trusted.authentication().principal().principalId());
    }

    private ReviewGovernedAccessRequestCommand review(UiCapabilityApiContext trusted, String requestId,
            String requestVersion, long grantVersion, long resourceVersion, String reason, String idempotencyKey) {
        return new ReviewGovernedAccessRequestCommand(trusted.authentication(), requestId,
                parseVersion(requestVersion), grantVersion, resourceVersion, reason, trusted.correlationId(),
                idempotencyKey, trusted.requestedAt());
    }
    private static long parseVersion(String value) {
        String normalized = value == null ? "" : value.trim().replace("\"", "");
        if (normalized.startsWith("W/")) normalized = normalized.substring(2);
        try { return Long.parseLong(normalized); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("If-Match must contain the numeric request version"); }
    }
    private static ResponseEntity<UiAccessRequestResponse> noStore(UiAccessRequestResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.VARY, "Cookie").header("X-Content-Type-Options", "nosniff").body(body);
    }
    public record SubmitBody(String uiActionId, String resourceId, long expectedResourceVersion,
                             VisibilityLevel requestedVisibility, long durationHours, String businessPurpose) { }
}
