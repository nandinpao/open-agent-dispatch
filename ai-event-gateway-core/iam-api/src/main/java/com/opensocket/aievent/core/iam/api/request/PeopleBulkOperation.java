package com.opensocket.aievent.core.iam.api.request;

/** Server-owned bulk operations for enterprise Person and Organization administration. */
public enum PeopleBulkOperation {
    MOVE_PRIMARY_DEPARTMENT,
    ADD_DEPARTMENT_MEMBERSHIP,
    ADD_GROUPS,
    REMOVE_GROUPS,
    SUSPEND,
    REACTIVATE,
    REVOKE_SESSIONS,
    RESEND_INVITATION
}
