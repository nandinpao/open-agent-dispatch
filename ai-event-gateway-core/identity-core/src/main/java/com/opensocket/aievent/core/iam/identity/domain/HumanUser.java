package com.opensocket.aievent.core.iam.identity.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable Human User aggregate. Credentials and tenant memberships live in other boundaries. */
public final class HumanUser {
    private static final Map<AccountStatus, Set<AccountStatus>> ALLOWED_TRANSITIONS = transitions();

    private final UserId userId;
    private final Username username;
    private final Optional<EmailAddress> email;
    private final String displayName;
    private final AccountStatus status;
    private final UserCreationMode creationMode;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;
    private final String statusReason;
    private final long version;

    private HumanUser(UserId userId, Username username, Optional<EmailAddress> email, String displayName,
                      AccountStatus status, UserCreationMode creationMode, Instant createdAt, Instant updatedAt,
                      String createdBy, String updatedBy, String statusReason, long version) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.username = Objects.requireNonNull(username, "username");
        this.email = email == null ? Optional.empty() : email;
        this.displayName = DomainText.required(displayName, "displayName", 200);
        this.status = Objects.requireNonNull(status, "status");
        this.creationMode = Objects.requireNonNull(creationMode, "creationMode");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        this.createdBy = DomainText.required(createdBy, "createdBy", 128);
        this.updatedBy = DomainText.required(updatedBy, "updatedBy", 128);
        this.statusReason = DomainText.optional(statusReason, 500);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
    }

    public static HumanUser administrativelyCreated(UserId userId, Username username, EmailAddress email,
                                                     String displayName, String actorId, Instant at) {
        return initial(userId, username, email, displayName, AccountStatus.ACTIVE,
                UserCreationMode.ADMIN_CREATED, actorId, at);
    }

    public static HumanUser invited(UserId userId, Username username, EmailAddress email,
                                    String displayName, String actorId, Instant at) {
        if (email == null) throw new IllegalArgumentException("email is required for an invitation");
        return initial(userId, username, email, displayName, AccountStatus.PENDING_ACTIVATION,
                UserCreationMode.INVITATION, actorId, at);
    }

    public static HumanUser legacyImported(UserId userId, Username username, EmailAddress email,
                                           String displayName, String actorId, Instant at) {
        return initial(userId, username, email, displayName, AccountStatus.PASSWORD_RESET_REQUIRED,
                UserCreationMode.LEGACY_IMPORT, actorId, at);
    }

    public static HumanUser reconstitute(UserId userId, Username username, Optional<EmailAddress> email,
                                         String displayName, AccountStatus status, UserCreationMode creationMode,
                                         Instant createdAt, Instant updatedAt, String createdBy, String updatedBy,
                                         String statusReason, long version) {
        return new HumanUser(userId, username, email, displayName, status, creationMode, createdAt, updatedAt,
                createdBy, updatedBy, statusReason, version);
    }

    private static HumanUser initial(UserId userId, Username username, EmailAddress email, String displayName,
                                     AccountStatus status, UserCreationMode mode, String actorId, Instant at) {
        Objects.requireNonNull(at, "at");
        return new HumanUser(userId, username, Optional.ofNullable(email), displayName, status, mode, at, at,
                actorId, actorId, "", 1);
    }

    public HumanUser changeStatus(AccountStatus target, String actorId, String reason, Instant at) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
        String checkedReason = DomainText.required(reason, "reason", 500);
        if (target == status) return this;
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new IdentityDomainException(IdentityReasonCode.INVALID_STATUS_TRANSITION,
                    "User status transition " + status + " -> " + target + " is not allowed");
        }
        return new HumanUser(userId, username, email, displayName, target, creationMode, createdAt, at,
                createdBy, actorId, checkedReason, version + 1);
    }

    public HumanUser updateProfile(String newDisplayName, EmailAddress newEmail, String actorId, Instant at) {
        Objects.requireNonNull(at, "at");
        if (status == AccountStatus.DELETED) {
            throw new IdentityDomainException(IdentityReasonCode.INVALID_STATUS_TRANSITION,
                    "Deleted users cannot be updated");
        }
        return new HumanUser(userId, username, Optional.ofNullable(newEmail), newDisplayName, status, creationMode,
                createdAt, at, createdBy, actorId, statusReason, version + 1);
    }

    public IdentityType identityType() { return IdentityType.HUMAN_USER; }
    public UserId userId() { return userId; }
    public Username username() { return username; }
    public Optional<EmailAddress> email() { return email; }
    public String displayName() { return displayName; }
    public AccountStatus status() { return status; }
    public UserCreationMode creationMode() { return creationMode; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public String statusReason() { return statusReason; }
    public long version() { return version; }
    public boolean active() { return status == AccountStatus.ACTIVE; }

    private static Map<AccountStatus, Set<AccountStatus>> transitions() {
        EnumMap<AccountStatus, Set<AccountStatus>> result = new EnumMap<>(AccountStatus.class);
        result.put(AccountStatus.PENDING_ACTIVATION, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.DISABLED,
                AccountStatus.PASSWORD_RESET_REQUIRED, AccountStatus.MFA_ENROLLMENT_REQUIRED, AccountStatus.DELETED));
        result.put(AccountStatus.ACTIVE, EnumSet.of(AccountStatus.LOCKED, AccountStatus.SUSPENDED, AccountStatus.DISABLED,
                AccountStatus.PASSWORD_RESET_REQUIRED, AccountStatus.MFA_ENROLLMENT_REQUIRED, AccountStatus.DELETED));
        result.put(AccountStatus.LOCKED, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.SUSPENDED, AccountStatus.DISABLED,
                AccountStatus.PASSWORD_RESET_REQUIRED, AccountStatus.DELETED));
        result.put(AccountStatus.SUSPENDED, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.DISABLED, AccountStatus.DELETED));
        result.put(AccountStatus.DISABLED, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.DELETED));
        result.put(AccountStatus.PASSWORD_RESET_REQUIRED, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.MFA_ENROLLMENT_REQUIRED,
                AccountStatus.SUSPENDED, AccountStatus.DISABLED, AccountStatus.DELETED));
        result.put(AccountStatus.MFA_ENROLLMENT_REQUIRED, EnumSet.of(AccountStatus.ACTIVE, AccountStatus.PASSWORD_RESET_REQUIRED,
                AccountStatus.SUSPENDED, AccountStatus.DISABLED, AccountStatus.DELETED));
        result.put(AccountStatus.DELETED, EnumSet.noneOf(AccountStatus.class));
        return Map.copyOf(result);
    }
}
