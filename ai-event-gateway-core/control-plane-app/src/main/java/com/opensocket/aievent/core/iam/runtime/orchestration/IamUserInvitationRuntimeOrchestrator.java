package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.port.IamActivationDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserInvitationApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.response.TokenSummaryResponse;
import com.opensocket.aievent.core.iam.api.response.UserInvitationStatusResponse;
import com.opensocket.aievent.core.iam.api.response.UserResponse;
import com.opensocket.aievent.core.iam.token.application.command.IssueOneTimeTokenCommand;
import com.opensocket.aievent.core.iam.token.application.command.RevokeAccessTokenCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Tenant-scoped invitation lifecycle authority. A plaintext setup URL is returned only for explicit MANUAL delivery and is never persisted or logged. */
public final class IamUserInvitationRuntimeOrchestrator implements IamUserInvitationApiPort {
    private static final int TOKEN_SCAN_LIMIT = 100;
    private static final Duration INVITATION_TTL = Duration.ofHours(24);

    private final IamAdministrationProjectionPort projections;
    private final AccessTokenCommandPort tokens;
    private final IamActivationDeliveryPort delivery;
    private final IamIdempotencyExecutor idempotency;
    private final Clock clock;

    public IamUserInvitationRuntimeOrchestrator(
            IamAdministrationProjectionPort projections,
            AccessTokenCommandPort tokens,
            IamActivationDeliveryPort delivery,
            IamIdempotencyExecutor idempotency,
            Clock clock) {
        this.projections = projections;
        this.tokens = tokens;
        this.delivery = delivery;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Override
    public UserInvitationStatusResponse status(String userId, IamApiRequestContext context) {
        UserResponse user = requireUser(context.activeTenantId(), userId);
        return statusOf(context.activeTenantId(), user, clock.instant());
    }

    @Override
    public UserInvitationStatusResponse resend(String userId, String deliveryMethod, IamApiRequestContext context) {
        String tenantId = context.activeTenantId();
        String key = context.requireIdempotencyKey();
        String reason = context.requireAuditReason();
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.user.invitation.resend",
                key,
                Map.of("userId", userId, "deliveryMethod", deliveryMethod == null ? "" : deliveryMethod, "reason", reason),
                200,
                UserInvitationStatusResponse.class,
                () -> {
                    UserResponse user = requireUser(tenantId, userId);
                    if ("DELETED".equals(user.status())) {
                        throw new IllegalArgumentException("IDENTITY_INVITATION_USER_CLOSED");
                    }
                    if (!invitationEligible(user.status())) {
                        throw new IllegalArgumentException("IDENTITY_INVITATION_USER_NOT_ELIGIBLE");
                    }
                    revokeActiveInvitations(tenantId, userId, reason, context);
                    var issued = tokens.issueOneTime(new IssueOneTimeTokenCommand(
                            tenantId,
                            userId,
                            AccessTokenType.INVITATION_TOKEN,
                            INVITATION_TTL,
                            context.actorId(),
                            context.correlationId()));
                    String recipient = deliveryReference(user);
                    var receipt = delivery.deliver(new IamActivationDeliveryPort.DeliveryCommand(
                            tenantId, userId, issued.tokenId(), "USER_INVITATION", deliveryMethod, recipient,
                            issued.token(), issued.expiresAt(), context.actorId(), context.correlationId()));
                    return new UserInvitationStatusResponse(
                            userId,
                            "PENDING",
                            receipt.recipientReference(),
                            issued.tokenId(),
                            clock.instant(),
                            issued.expiresAt(),
                            true,
                            true,
                            receipt.deliveryMethod(),
                            receipt.deliveryStatus(),
                            receipt.failureCode(),
                            receipt.setupActionUrl());
                });
    }

    @Override
    public UserInvitationStatusResponse revoke(String userId, IamApiRequestContext context) {
        String tenantId = context.activeTenantId();
        String key = context.requireIdempotencyKey();
        String reason = context.requireAuditReason();
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.user.invitation.revoke",
                key,
                Map.of("userId", userId, "reason", reason),
                200,
                UserInvitationStatusResponse.class,
                () -> {
                    UserResponse user = requireUser(tenantId, userId);
                    revokeActiveInvitations(tenantId, userId, reason, context);
                    return statusOf(tenantId, user, clock.instant());
                });
    }

    private UserInvitationStatusResponse statusOf(String tenantId, UserResponse user, Instant now) {
        List<TokenSummaryResponse> invitationTokens = invitations(tenantId, user.userId());
        if (invitationTokens.isEmpty()) {
            return UserInvitationStatusResponse.notIssued(
                    user.userId(), deliveryReference(user), invitationEligible(user.status()));
        }
        TokenSummaryResponse latest = invitationTokens.stream()
                .max(Comparator.comparing(TokenSummaryResponse::issuedAt))
                .orElseThrow();
        String status = normalizeStatus(latest, now);
        boolean invitationEligible = invitationEligible(user.status());
        var readiness = projections.userAuthenticationReadiness(tenantId, user.userId());
        return new UserInvitationStatusResponse(
                user.userId(),
                status,
                readiness.deliveryReference().isBlank() ? deliveryReference(user) : readiness.deliveryReference(),
                latest.tokenId(),
                latest.issuedAt(),
                latest.expiresAt(),
                invitationEligible && !"ACCEPTED".equals(status),
                "PENDING".equals(status),
                readiness.deliveryMethod(),
                readiness.deliveryStatus(),
                readiness.deliveryFailureCode(),
                "");
    }

    private List<TokenSummaryResponse> invitations(String tenantId, String userId) {
        return projections.tokens(tenantId, userId, TOKEN_SCAN_LIMIT, "", "").items().stream()
                .filter(token -> AccessTokenType.INVITATION_TOKEN.name().equals(token.tokenType()))
                .toList();
    }

    private void revokeActiveInvitations(
            String tenantId,
            String userId,
            String reason,
            IamApiRequestContext context) {
        for (TokenSummaryResponse invitation : invitations(tenantId, userId)) {
            if ("ACTIVE".equals(invitation.status())) {
                tokens.revoke(new RevokeAccessTokenCommand(
                        tenantId,
                        invitation.tokenId(),
                        reason,
                        context.actorId(),
                        context.correlationId()));
            }
        }
    }

    private UserResponse requireUser(String tenantId, String userId) {
        return projections.user(tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
    }

    private static boolean invitationEligible(String userStatus) {
        // Invitations are only for identities that have not established their first password yet.
        // Password-reset-required users use the setup/reset credential flow, while MFA-enrollment-
        // required users resume the restricted MFA ceremony. Re-issuing an invitation after either
        // transition would incorrectly reopen initial password activation.
        return "PENDING_ACTIVATION".equals(userStatus);
    }

    private static String deliveryReference(UserResponse user) {
        return user.email() == null || user.email().isBlank() ? user.username() : user.email();
    }

    private static String normalizeStatus(TokenSummaryResponse token, Instant now) {
        if ("CONSUMED".equals(token.status())) return "ACCEPTED";
        if ("REVOKED".equals(token.status())) return "REVOKED";
        if ("EXPIRED".equals(token.status())
                || token.expiresAt() == null
                || !token.expiresAt().isAfter(now)) return "EXPIRED";
        return "ACTIVE".equals(token.status()) ? "PENDING" : token.status();
    }
}
