package com.opensocket.aievent.core.iam.identity.application.service;

import com.opensocket.aievent.core.iam.identity.application.command.ChangeHumanUserStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.command.ChangeRootIdentityStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.command.CreateHumanUserCommand;
import com.opensocket.aievent.core.iam.identity.application.command.CreateRootIdentityCommand;
import com.opensocket.aievent.core.iam.identity.application.command.UpdateHumanUserProfileCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.out.HumanUserRepository;
import com.opensocket.aievent.core.iam.identity.application.port.out.IdentityEventPublisher;
import com.opensocket.aievent.core.iam.identity.application.port.out.RootIdentityRepository;
import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.identity.domain.EmailAddress;
import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import com.opensocket.aievent.core.iam.identity.domain.IdentityDomainException;
import com.opensocket.aievent.core.iam.identity.domain.IdentityReasonCode;
import com.opensocket.aievent.core.iam.identity.domain.RootIdentity;
import com.opensocket.aievent.core.iam.identity.domain.RootIdentityId;
import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import com.opensocket.aievent.core.iam.identity.domain.UserId;
import com.opensocket.aievent.core.iam.identity.domain.Username;
import com.opensocket.aievent.core.iam.identity.event.RootIdentityCreatedEvent;
import com.opensocket.aievent.core.iam.identity.event.RootIdentityStatusChangedEvent;
import com.opensocket.aievent.core.iam.identity.event.UserCreatedEvent;
import com.opensocket.aievent.core.iam.identity.event.UserInvitedEvent;
import com.opensocket.aievent.core.iam.identity.event.UserStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Framework-free application service. Transaction demarcation is supplied by the future adapter/orchestrator. */
public final class IdentityApplicationService implements IdentityCommandPort {
    private final HumanUserRepository users;
    private final RootIdentityRepository roots;
    private final IdentityEventPublisher events;
    private final Clock clock;

    public IdentityApplicationService(HumanUserRepository users, RootIdentityRepository roots,
                                      IdentityEventPublisher events, Clock clock) {
        this.users = Objects.requireNonNull(users, "users");
        this.roots = Objects.requireNonNull(roots, "roots");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public HumanUser createHumanUser(CreateHumanUserCommand command) {
        Objects.requireNonNull(command, "command");
        UserId id = new UserId(command.userId());
        Username username = new Username(command.username());
        EmailAddress email = command.email() == null || command.email().isBlank() ? null : new EmailAddress(command.email());
        if (users.findById(id).isPresent() || users.existsByNormalizedUsername(username.normalizedValue())) {
            throw new IdentityDomainException(IdentityReasonCode.USERNAME_CONFLICT, "User id or username already exists");
        }
        if (email != null && users.existsByNormalizedEmail(email.normalizedValue())) {
            throw new IdentityDomainException(IdentityReasonCode.EMAIL_CONFLICT, "Email already exists");
        }
        Instant now = clock.instant();
        UserCreationMode mode = Objects.requireNonNull(command.creationMode(), "creationMode");
        HumanUser user = switch (mode) {
            case ADMIN_CREATED -> HumanUser.administrativelyCreated(id, username, email, command.displayName(), command.actorId(), now);
            case INVITATION -> HumanUser.invited(id, username, email, command.displayName(), command.actorId(), now);
            case LEGACY_IMPORT -> HumanUser.legacyImported(id, username, email, command.displayName(), command.actorId(), now);
        };
        HumanUser saved = users.save(user, 0);
        events.publish(new UserCreatedEvent(command.eventId(), id.value(), username.value(), saved.status(), mode,
                command.actorId(), command.correlationId(), now));
        if (mode == UserCreationMode.INVITATION) {
            events.publish(new UserInvitedEvent(command.eventId() + ":invited", id.value(), username.value(),
                    email.normalizedValue(), command.actorId(), command.correlationId(), now));
        }
        return saved;
    }

    @Override
    public HumanUser changeHumanUserStatus(ChangeHumanUserStatusCommand command) {
        Objects.requireNonNull(command, "command");
        HumanUser current = requireUser(command.userId());
        requireVersion(current.version(), command.expectedVersion());
        AccountStatus previous = current.status();
        HumanUser changed = current.changeStatus(command.targetStatus(), command.actorId(), command.reason(), clock.instant());
        HumanUser saved = users.save(changed, command.expectedVersion());
        events.publish(new UserStatusChangedEvent(command.eventId(), saved.userId().value(), previous, saved.status(),
                command.reason(), saved.version(), command.actorId(), command.correlationId(), saved.updatedAt()));
        return saved;
    }

    @Override
    public HumanUser updateHumanUserProfile(UpdateHumanUserProfileCommand command) {
        Objects.requireNonNull(command, "command");
        HumanUser current = requireUser(command.userId());
        requireVersion(current.version(), command.expectedVersion());
        EmailAddress email = command.email() == null || command.email().isBlank() ? null : new EmailAddress(command.email());
        if (email != null && (!current.email().map(EmailAddress::normalizedValue).orElse("").equals(email.normalizedValue()))
                && users.existsByNormalizedEmail(email.normalizedValue())) {
            throw new IdentityDomainException(IdentityReasonCode.EMAIL_CONFLICT, "Email already exists");
        }
        return users.save(current.updateProfile(command.displayName(), email, command.actorId(), clock.instant()),
                command.expectedVersion());
    }

    @Override
    public RootIdentity createRootIdentity(CreateRootIdentityCommand command) {
        Objects.requireNonNull(command, "command");
        if (roots.find().isPresent()) throw new IllegalStateException("root identity already exists");
        Instant now = clock.instant();
        RootIdentity saved = roots.save(RootIdentity.bootstrapPending(command.actorId(), now), 0);
        events.publish(new RootIdentityCreatedEvent(command.eventId(), RootIdentityId.INSTANCE.value(), command.actorId(),
                command.correlationId(), now));
        return saved;
    }

    @Override
    public RootIdentity changeRootIdentityStatus(ChangeRootIdentityStatusCommand command) {
        Objects.requireNonNull(command, "command");
        RootIdentity current = roots.find().orElseThrow(() -> new IllegalStateException("root identity does not exist"));
        requireVersion(current.version(), command.expectedVersion());
        var previous = current.status();
        RootIdentity saved = roots.save(current.changeStatus(command.targetStatus(), command.actorId(), command.reason(),
                clock.instant()), command.expectedVersion());
        events.publish(new RootIdentityStatusChangedEvent(command.eventId(), RootIdentityId.INSTANCE.value(), previous,
                saved.status(), command.reason(), saved.version(), command.actorId(), command.correlationId(), saved.updatedAt()));
        return saved;
    }

    private HumanUser requireUser(String userId) {
        return users.findById(new UserId(userId)).orElseThrow(() -> new IllegalArgumentException("user not found"));
    }

    private static void requireVersion(long actual, long expected) {
        if (expected < 1 || actual != expected) {
            throw new IdentityDomainException(IdentityReasonCode.VERSION_CONFLICT,
                    "Expected version " + expected + " but was " + actual);
        }
    }
}
