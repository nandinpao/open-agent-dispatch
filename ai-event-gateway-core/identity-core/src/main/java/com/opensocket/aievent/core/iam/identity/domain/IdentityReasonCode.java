package com.opensocket.aievent.core.iam.identity.domain;

public final class IdentityReasonCode {
    public static final String USERNAME_CONFLICT = "IDENTITY_USERNAME_CONFLICT";
    public static final String EMAIL_CONFLICT = "IDENTITY_EMAIL_CONFLICT";
    public static final String INVALID_STATUS_TRANSITION = "IDENTITY_INVALID_STATUS_TRANSITION";
    public static final String EXISTING_USER_NOT_ADMITTABLE = "IDENTITY_EXISTING_USER_NOT_ADMITTABLE";
    public static final String IDENTITY_ATTRIBUTES_CONFLICT = "IDENTITY_ATTRIBUTES_CONFLICT";
    public static final String VERSION_CONFLICT = "IDENTITY_VERSION_CONFLICT";
    public static final String ROOT_STATUS_TRANSITION_FORBIDDEN = "ROOT_STATUS_TRANSITION_FORBIDDEN";

    private IdentityReasonCode() { }
}
