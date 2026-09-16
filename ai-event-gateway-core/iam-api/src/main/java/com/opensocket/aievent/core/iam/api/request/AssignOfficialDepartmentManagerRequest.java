package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.Size;

/** Assigns or clears the organizational manager relationship; it never assigns an RBAC Role. */
public record AssignOfficialDepartmentManagerRequest(
        @Size(max = 128) String managerUserId) {
    public String normalizedManagerUserId() {
        return managerUserId == null ? "" : managerUserId.trim();
    }
}
