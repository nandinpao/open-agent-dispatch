package com.opensocket.aievent.core.issuetracking.identity;
/** Provider actor policy. Provider credentials remain an Issue Tracking authority. */
public enum ProviderWriteIdentityPolicy { SERVICE_ACCOUNT_ON_BEHALF_OF, USER_DELEGATED_IF_VERIFIED, SYSTEM_AUTOMATION, WRITE_PROHIBITED }
