package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.port.IamPlatformUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserInvitationApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;
import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembershipType;
import com.opensocket.aievent.core.iam.organization.domain.GroupMembershipRole;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Enterprise bulk command coordinator.
 *
 * <p>The browser submits one governed command. This service performs authorization and orchestration
 * on the server and returns a deterministic per-Person reconciliation result. Each Person receives an
 * independent reconciliation/idempotency namespace. Authoritative sub-mutations retain their existing
 * transaction boundaries; the batch intentionally uses PARTIAL_SUCCESS rather than one long database
 * transaction.</p>
 */
public final class IamPeopleBulkAdministrationService {
    private static final int PAGE_LIMIT = 100;
    private static final int MAX_MEMBERSHIP_PAGES = 20;

    private final IamOrganizationAdministrationService organization;
    private final IamAdministrationProjectionPort projections;
    private final IamPlatformUserAdministrationApiPort platformUsers;
    private final IamSessionAdministrationApiPort sessions;
    private final IamUserInvitationApiPort invitations;
    private final IamPermissionGuard guard;

    public IamPeopleBulkAdministrationService(
            IamOrganizationAdministrationService organization,
            IamAdministrationProjectionPort projections,
            IamPlatformUserAdministrationApiPort platformUsers,
            IamSessionAdministrationApiPort sessions,
            IamUserInvitationApiPort invitations,
            IamPermissionGuard guard) {
        this.organization = organization;
        this.projections = projections;
        this.platformUsers = platformUsers;
        this.sessions = sessions;
        this.invitations = invitations;
        this.guard = guard;
    }

    public PeopleBulkActionResponse execute(PeopleBulkActionRequest request, IamApiRequestContext context) {
        String tenantId = context.activeTenantId();
        context.requireAuditReason();
        String parentKey = context.requireIdempotencyKey();
        validateTargets(request);
        preflightDestinationAuthorization(request, context);

        List<PeopleBulkItemResultResponse> results = new ArrayList<>();
        int changedTotal = 0;
        for (String userId : request.userIds()) {
            UserResponse person = projections.user(tenantId, userId).orElse(null);
            String displayName = person == null || person.displayName() == null || person.displayName().isBlank()
                    ? userId : person.displayName();
            if (person == null) {
                results.add(failed(userId, displayName, "IDENTITY_USER_NOT_FOUND",
                        "The Person is not an active member of this workspace.",
                        "Refresh the People list and remove this Person from the batch."));
                continue;
            }
            try {
                int changed = executeOne(request, person, context);
                changedTotal += changed;
                if (changed == 0) {
                    results.add(new PeopleBulkItemResultResponse(userId, displayName, "SKIPPED", 0,
                            "IAM_BULK_NO_CHANGE", "The requested state is already effective.",
                            "No action is required."));
                } else {
                    results.add(new PeopleBulkItemResultResponse(userId, displayName, "SUCCEEDED", changed,
                            "", "The requested governed change completed.", ""));
                }
            } catch (IamApiException ex) {
                results.add(failed(userId, displayName, ex.errorCode(), safeMessage(ex.getMessage()), remediation(ex.status().value())));
            } catch (IllegalArgumentException | IllegalStateException ex) {
                String code = normalizedCode(ex.getMessage());
                results.add(failed(userId, displayName, code, businessMessage(code), remediationForCode(code)));
            } catch (RuntimeException ex) {
                results.add(failed(userId, displayName, "IAM_BULK_ITEM_FAILED",
                        "The Person could not be changed because the authoritative state changed or the operation was rejected.",
                        "Refresh this Person and retry the failed item. Use the correlation ID if support review is required."));
            }
        }

        int succeeded = (int) results.stream().filter(item -> "SUCCEEDED".equals(item.outcome())).count();
        int skipped = (int) results.stream().filter(item -> "SKIPPED".equals(item.outcome())).count();
        int failed = results.size() - succeeded - skipped;
        String operationId = "bulk-" + UUID.nameUUIDFromBytes((tenantId + "|" + context.actorId() + "|" + parentKey)
                .getBytes(StandardCharsets.UTF_8));
        return new PeopleBulkActionResponse(operationId, request.operation().name(), "PARTIAL_SUCCESS",
                request.userIds().size(), succeeded, skipped, failed, changedTotal, results);
    }

    private int executeOne(PeopleBulkActionRequest request, UserResponse person, IamApiRequestContext root) {
        String userId = person.userId();
        return switch (request.operation()) {
            case MOVE_PRIMARY_DEPARTMENT -> movePrimaryDepartment(userId, request.targetDepartmentId(), root);
            case ADD_DEPARTMENT_MEMBERSHIP -> addDepartmentMembership(userId, request.targetDepartmentId(), root);
            case ADD_GROUPS -> changeGroups(userId, request.groupIds(), request.membershipRole(), true, root);
            case REMOVE_GROUPS -> changeGroups(userId, request.groupIds(), request.membershipRole(), false, root);
            case SUSPEND -> changeIdentityStatus(person, AccountStatus.SUSPENDED, root);
            case REACTIVATE -> changeIdentityStatus(person, AccountStatus.ACTIVE, root);
            case REVOKE_SESSIONS -> revokeSessions(userId, root);
            case RESEND_INVITATION -> resendInvitation(userId, request.deliveryMethod(), root);
        };
    }

    private int movePrimaryDepartment(String userId, String targetDepartmentId, IamApiRequestContext root) {
        List<MembershipResponse> departments = memberships(userId, root).stream()
                .filter(item -> "DEPARTMENT".equals(item.membershipType()) && "ACTIVE".equals(item.status()))
                .toList();
        MembershipResponse primary = departments.stream().filter(MembershipResponse::primary).findFirst().orElse(null);
        if (targetDepartmentId == null || targetDepartmentId.isBlank()) {
            if (primary == null) return 0;
            guard.requireDepartment(root, IamPermissions.MEMBERSHIP_MANAGE, "MEMBERSHIP", primary.resourceId());
            organization.updateDepartmentMembership(primary.membershipId(),
                    new UpdateDepartmentMembershipRequest(DepartmentMembershipType.valueOf(primary.role()), false, primary.expiresAt(),
                            null, null, root.requireAuditReason()), primary.version(), true,
                    child(root, userId, "move-unassigned-" + primary.membershipId()));
            return 1;
        }
        if (primary != null && targetDepartmentId.equals(primary.resourceId())) return 0;

        MembershipResponse target = departments.stream().filter(item -> targetDepartmentId.equals(item.resourceId())).findFirst().orElse(null);
        if (target == null) {
            target = organization.addDepartmentMembership(userId,
                    new AddDepartmentMembershipRequest(null, targetDepartmentId, DepartmentMembershipType.MEMBER, primary == null, null),
                    child(root, userId, "move-add-target-" + targetDepartmentId));
        }
        if (primary != null) {
            guard.requireDepartment(root, IamPermissions.MEMBERSHIP_MANAGE, "MEMBERSHIP", primary.resourceId());
            organization.updateDepartmentMembership(primary.membershipId(),
                    new UpdateDepartmentMembershipRequest(DepartmentMembershipType.valueOf(primary.role()), false, primary.expiresAt(),
                            target.membershipId(), target.version(), root.requireAuditReason()), primary.version(), true,
                    child(root, userId, "move-remove-primary-" + primary.membershipId()));
            return 1;
        }
        if (!target.primary()) {
            organization.updateDepartmentMembership(target.membershipId(),
                    new UpdateDepartmentMembershipRequest(DepartmentMembershipType.valueOf(target.role()), true, target.expiresAt(),
                            null, null, root.requireAuditReason()), target.version(), false,
                    child(root, userId, "move-promote-target-" + target.membershipId()));
            return 1;
        }
        return 0;
    }

    private int addDepartmentMembership(String userId, String departmentId, IamApiRequestContext root) {
        boolean exists = memberships(userId, root).stream().anyMatch(item -> "DEPARTMENT".equals(item.membershipType())
                && "ACTIVE".equals(item.status()) && departmentId.equals(item.resourceId()));
        if (exists) return 0;
        organization.addDepartmentMembership(userId,
                new AddDepartmentMembershipRequest(null, departmentId, DepartmentMembershipType.MEMBER, false, null),
                child(root, userId, "add-department-" + departmentId));
        return 1;
    }

    private int changeGroups(String userId, List<String> groupIds, String membershipRole, boolean add, IamApiRequestContext root) {
        List<MembershipResponse> current = memberships(userId, root).stream()
                .filter(item -> "GROUP".equals(item.membershipType()) && "ACTIVE".equals(item.status())).toList();
        int changed = 0;
        for (String groupId : groupIds) {
            MembershipResponse membership = current.stream().filter(item -> groupId.equals(item.resourceId())).findFirst().orElse(null);
            if (add && membership == null) {
                organization.addGroupMembership(userId,
                        new AddGroupMembershipRequest(null, groupId, GroupMembershipRole.valueOf(membershipRole), null),
                        child(root, userId, "add-group-" + groupId));
                changed++;
            } else if (!add && membership != null) {
                guard.requireGroup(root, IamPermissions.MEMBERSHIP_MANAGE, "MEMBERSHIP", membership.resourceId());
                organization.updateGroupMembership(membership.membershipId(),
                        new UpdateGroupMembershipRequest(GroupMembershipRole.valueOf(membership.role()), membership.expiresAt(), root.requireAuditReason()),
                        membership.version(), true, child(root, userId, "remove-group-" + groupId));
                changed++;
            }
        }
        return changed;
    }

    private int changeIdentityStatus(UserResponse person, AccountStatus target, IamApiRequestContext root) {
        if (target.name().equals(person.status())) return 0;
        guard.requireInstance(root, IamPermissions.PLATFORM_USER_UPDATE, "USER", person.userId());
        platformUsers.changeUserStatus(person.userId(), new ChangeUserStatusRequest(target, root.requireAuditReason()), person.version(),
                child(root, person.userId(), "identity-status-" + target.name()));
        return 1;
    }

    private int revokeSessions(String userId, IamApiRequestContext root) {
        guard.requireTenant(root, IamPermissions.SESSION_REVOKE, "USER", userId);
        sessions.revokeAllForUser(userId, root.requireAuditReason(), child(root, userId, "revoke-sessions"));
        return 1;
    }

    private int resendInvitation(String userId, String deliveryMethod, IamApiRequestContext root) {
        guard.requireTenant(root, IamPermissions.USER_UPDATE, "USER", userId);
        guard.requireTenant(root, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "USER", userId);
        invitations.resend(userId, deliveryMethod, child(root, userId, "resend-invitation"));
        return 1;
    }

    private List<MembershipResponse> memberships(String userId, IamApiRequestContext root) {
        List<MembershipResponse> all = new ArrayList<>();
        String cursor = "";
        for (int page = 0; page < MAX_MEMBERSHIP_PAGES; page++) {
            var result = projections.memberships(root.activeTenantId(), userId, PAGE_LIMIT, cursor);
            all.addAll(result.items());
            if (!result.hasMore() || result.nextCursor().isBlank()) break;
            cursor = result.nextCursor();
        }
        return all;
    }

    private void validateTargets(PeopleBulkActionRequest request) {
        if (request.operation() == PeopleBulkOperation.ADD_DEPARTMENT_MEMBERSHIP
                && request.targetDepartmentId().isBlank()) {
            throw IamApiException.badRequest("IAM_BULK_TARGET_DEPARTMENT_REQUIRED", "A Department is required for this bulk operation");
        }
        if ((request.operation() == PeopleBulkOperation.ADD_GROUPS || request.operation() == PeopleBulkOperation.REMOVE_GROUPS)
                && request.groupIds().isEmpty()) {
            throw IamApiException.badRequest("IAM_BULK_TARGET_GROUP_REQUIRED", "At least one Group is required for this bulk operation");
        }
        if (request.operation() == PeopleBulkOperation.ADD_GROUPS || request.operation() == PeopleBulkOperation.REMOVE_GROUPS) {
            try {
                GroupMembershipRole.valueOf(request.membershipRole());
            } catch (IllegalArgumentException ex) {
                throw IamApiException.badRequest("IAM_BULK_GROUP_ROLE_INVALID", "Group membership role must be MEMBER or LEAD");
            }
        }
        if (request.operation() == PeopleBulkOperation.RESEND_INVITATION
                && !("EMAIL".equals(request.deliveryMethod()) || "MANUAL".equals(request.deliveryMethod())
                || "DEVELOPMENT_FILE".equals(request.deliveryMethod()))) {
            throw IamApiException.badRequest("IAM_BULK_DELIVERY_METHOD_INVALID",
                    "Invitation delivery method must be EMAIL, MANUAL, or DEVELOPMENT_FILE");
        }
    }

    private void preflightDestinationAuthorization(PeopleBulkActionRequest request, IamApiRequestContext context) {
        if ((request.operation() == PeopleBulkOperation.MOVE_PRIMARY_DEPARTMENT
                || request.operation() == PeopleBulkOperation.ADD_DEPARTMENT_MEMBERSHIP)
                && !request.targetDepartmentId().isBlank()) {
            guard.requireDepartment(context, IamPermissions.MEMBERSHIP_MANAGE, "BULK_MEMBERSHIP", request.targetDepartmentId());
        }
        if (request.operation() == PeopleBulkOperation.ADD_GROUPS || request.operation() == PeopleBulkOperation.REMOVE_GROUPS) {
            request.groupIds().forEach(groupId -> guard.requireGroup(context, IamPermissions.MEMBERSHIP_MANAGE, "BULK_MEMBERSHIP", groupId));
        }
    }

    private static IamApiRequestContext child(IamApiRequestContext root, String userId, String step) {
        String key = "bulk-item-" + UUID.nameUUIDFromBytes((root.requireIdempotencyKey() + "|" + userId + "|" + step)
                .getBytes(StandardCharsets.UTF_8));
        return new IamApiRequestContext(root.authentication(), root.correlationId(), key, root.auditReason(),
                root.clientAddress(), root.userAgent(), root.requestedAt(), root.credentialPermissionBoundary());
    }

    private static PeopleBulkItemResultResponse failed(String userId, String displayName, String code, String message, String remediation) {
        return new PeopleBulkItemResultResponse(userId, displayName, "FAILED", 0, code, message, remediation);
    }

    private static String normalizedCode(String raw) {
        if (raw == null || raw.isBlank()) return "IAM_BULK_ITEM_CONFLICT";
        String token = raw.trim().split("[:\\s]", 2)[0].toUpperCase(Locale.ROOT);
        return token.matches("[A-Z0-9_]{3,120}") ? token : "IAM_BULK_ITEM_CONFLICT";
    }

    private static String businessMessage(String code) {
        if (code.contains("VERSION") || code.contains("CONFLICT")) return "The Person or membership changed after the batch was prepared.";
        if (code.contains("NOT_FOUND")) return "The authoritative Person or membership no longer exists.";
        if (code.contains("DENIED") || code.contains("FORBIDDEN")) return "The current administrator is not authorized for this Person or organization scope.";
        return "The authoritative state rejected this change.";
    }

    private static String remediationForCode(String code) {
        if (code.contains("VERSION") || code.contains("CONFLICT") || code.contains("NOT_FOUND")) return "Refresh the Person and organization state, then retry only this failed item.";
        if (code.contains("DENIED") || code.contains("FORBIDDEN")) return "Request the required scope or remove this Person from the batch.";
        return "Review the Person details and retry the failed item; use the correlation ID for support diagnostics.";
    }

    private static String remediation(int status) {
        if (status == 403) return "Request the required administration scope or remove this Person from the batch.";
        if (status == 404 || status == 409 || status == 412 || status == 428) return "Refresh the Person and organization state, then retry only this failed item.";
        return "Review the Person details and retry the failed item.";
    }

    private static String safeMessage(String value) {
        return value == null || value.isBlank() ? "The authoritative service rejected this item." : value;
    }
}
