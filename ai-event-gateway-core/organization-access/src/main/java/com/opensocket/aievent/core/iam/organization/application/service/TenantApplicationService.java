package com.opensocket.aievent.core.iam.organization.application.service;

import com.opensocket.aievent.core.iam.organization.application.command.AddTenantMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.command.ChangeTenantMembershipStatusCommand;
import com.opensocket.aievent.core.iam.organization.application.command.ChangeTenantStatusCommand;
import com.opensocket.aievent.core.iam.organization.application.command.CreateTenantCommand;
import com.opensocket.aievent.core.iam.organization.application.command.UpdateTenantMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.out.OrganizationEventPublisher;
import com.opensocket.aievent.core.iam.organization.application.port.out.TenantMembershipRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.TenantRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentRepository;
import com.opensocket.aievent.core.iam.organization.domain.MembershipId;
import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.OrganizationDomainException;
import com.opensocket.aievent.core.iam.organization.domain.OrganizationReasonCode;
import com.opensocket.aievent.core.iam.organization.domain.Tenant;
import com.opensocket.aievent.core.iam.organization.domain.TenantId;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembership;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipEvidence;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import com.opensocket.aievent.core.iam.organization.event.OrganizationEvents;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class TenantApplicationService implements TenantCommandPort {
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final OrganizationEventPublisher events;
    private final DepartmentRepository departments;
    private final Clock clock;

    public TenantApplicationService(
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            OrganizationEventPublisher events,
            DepartmentRepository departments,
            Clock clock
    ) {
        this.tenants = Objects.requireNonNull(tenants, "tenants");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.events = Objects.requireNonNull(events, "events");
        this.departments = Objects.requireNonNull(departments, "departments");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Tenant createTenant(CreateTenantCommand command) {
        Objects.requireNonNull(command, "command");
        TenantId tenantId = new TenantId(command.tenantId());
        String normalizedCode = command.tenantCode().trim().toLowerCase(Locale.ROOT);
        if (tenants.findById(tenantId).isPresent() || tenants.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("tenant id or code already exists");
        }

        Locale locale = Locale.forLanguageTag(command.locale());
        if (locale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("locale must be a valid BCP 47 language tag");
        }

        Instant now = clock.instant();
        Tenant saved = tenants.save(
                Tenant.provision(
                        tenantId,
                        command.tenantCode(),
                        command.tenantName(),
                        command.legalName(),
                        ZoneId.of(command.timezone()),
                        locale,
                        command.dataRegion(),
                        command.actorId(),
                        now
                ),
                0
        );
        events.publish(new OrganizationEvents.TenantCreated(
                command.eventId(),
                tenantId.value(),
                tenantId.value(),
                command.actorId(),
                command.correlationId(),
                now
        ));
        return saved;
    }

    @Override
    public Tenant changeTenantStatus(ChangeTenantStatusCommand command) {
        Objects.requireNonNull(command, "command");
        Tenant current = tenants.findById(new TenantId(command.tenantId()))
                .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        requireVersion(current.version(), command.expectedVersion());

        var previousStatus = current.status();
        Tenant saved = tenants.save(
                current.changeStatus(command.targetStatus(), command.actorId(), clock.instant()),
                command.expectedVersion()
        );
        events.publish(new OrganizationEvents.TenantStatusChanged(
                command.eventId(),
                saved.tenantId().value(),
                saved.tenantId().value(),
                previousStatus.name(),
                saved.status().name(),
                command.actorId(),
                command.correlationId(),
                saved.updatedAt()
        ));
        return saved;
    }

    @Override
    public TenantMembership addTenantMembership(AddTenantMembershipCommand command) {
        Objects.requireNonNull(command, "command");
        TenantId tenantId = new TenantId(command.tenantId());
        PrincipalRef user = new PrincipalRef(PrincipalRef.PrincipalType.USER, command.userId());
        MembershipStatus initialStatus = command.initialStatus() == null
                ? MembershipStatus.ACTIVE
                : command.initialStatus();
        TenantMembershipSource source = command.membershipSource() == null
                ? TenantMembershipSource.ADMIN_CREATED
                : command.membershipSource();
        Optional<TenantMembership> existingMembership = memberships.find(tenantId, user);
        if (existingMembership.isPresent()) {
            TenantMembership current = existingMembership.orElseThrow();
            if (current.status() != MembershipStatus.REMOVED) {
                throw new OrganizationDomainException(
                        OrganizationReasonCode.TENANT_MEMBERSHIP_ALREADY_EXISTS,
                        "This person already has a current Tenant membership");
            }
            if (initialStatus != MembershipStatus.ACTIVE) {
                throw new OrganizationDomainException(
                        OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                        "An existing identity can only be re-admitted with ACTIVE Tenant membership");
            }
            Instant now = clock.instant();
            TenantMembership saved = memberships.save(
                    current.readmit(
                            command.employeeId(),
                            command.expiresAt(),
                            command.defaultTenant(),
                            source,
                            command.actorId(),
                            command.reason(),
                            now),
                    current.version());
            publishMembershipEvent(command.eventId(), saved, MembershipStatus.REMOVED, command.reason(), command.actorId(), command.correlationId());
            return saved;
        }

        Instant now = clock.instant();
        TenantMembership saved = memberships.save(
                TenantMembership.create(
                        new MembershipId(command.membershipId()),
                        tenantId,
                        user,
                        initialStatus,
                        command.employeeId(),
                        now,
                        command.expiresAt(),
                        command.defaultTenant(),
                        source,
                        command.actorId(),
                        command.reason()),
                0
        );
        publishMembershipEvent(command.eventId(), saved, null, command.reason(), command.actorId(), command.correlationId());
        return saved;
    }

    @Override
    public TenantMembership updateTenantMembership(UpdateTenantMembershipCommand command) {
        Objects.requireNonNull(command, "command");
        TenantMembership current = requireMembership(command.tenantId(), command.membershipId());
        requireVersion(current.version(), command.expectedVersion());
        if (command.expiresAt() != null && !command.expiresAt().isAfter(clock.instant())
                && !departments.findActiveManagedByUser(current.tenantId(), current.userPrincipal()).isEmpty()) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.OFFICIAL_MANAGER_ASSIGNMENT_BLOCKS_USER_STATUS,
                    "Official Department Manager Tenant access cannot expire before responsibilities are reassigned");
        }
        TenantMembership saved = memberships.save(
                current.updateDetails(
                        command.employeeId(),
                        command.expiresAt(),
                        command.defaultTenant(),
                        command.actorId(),
                        command.reason(),
                        clock.instant()),
                command.expectedVersion());
        publishMembershipEvent(command.eventId(), saved, current.status(), command.reason(), command.actorId(), command.correlationId());
        return saved;
    }

    @Override
    public TenantMembership changeTenantMembershipStatus(ChangeTenantMembershipStatusCommand command) {
        Objects.requireNonNull(command, "command");
        TenantMembership current = requireMembership(command.tenantId(), command.membershipId());
        requireVersion(current.version(), command.expectedVersion());
        Instant now = clock.instant();
        if (command.targetStatus() != MembershipStatus.ACTIVE
                && !departments.findActiveManagedByUser(current.tenantId(), current.userPrincipal()).isEmpty()) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.OFFICIAL_MANAGER_ASSIGNMENT_BLOCKS_USER_STATUS,
                    "Reassign all Official Department Manager responsibilities before suspending or removing Tenant access");
        }
        TenantMembership saved = memberships.save(
                current.changeStatus(
                        command.targetStatus(),
                        command.actorId(),
                        command.reason(),
                        now),
                command.expectedVersion());
        publishMembershipEvent(command.eventId(), saved, current.status(), command.reason(), command.actorId(), command.correlationId());
        return saved;
    }

    private TenantMembership requireMembership(String tenantId, String membershipId) {
        return memberships.findById(new TenantId(tenantId), new MembershipId(membershipId))
                .orElseThrow(() -> new IllegalArgumentException("IDENTITY_TENANT_MEMBERSHIP_NOT_FOUND"));
    }

    private void publishMembershipEvent(
            String eventId,
            TenantMembership membership,
            MembershipStatus previousStatus,
            String reason,
            String actorId,
            String correlationId) {
        memberships.appendEvidence(new TenantMembershipEvidence(
                eventId,
                membership.tenantId(),
                membership.membershipId(),
                membership.userPrincipal().principalId(),
                Optional.ofNullable(previousStatus),
                membership.status(),
                reason,
                actorId,
                correlationId,
                membership.version(),
                membership.updatedAt()));
        events.publish(new OrganizationEvents.TenantMembershipChanged(
                eventId,
                membership.tenantId().value(),
                membership.membershipId().value(),
                membership.userPrincipal().principalId(),
                membership.status().name(),
                actorId,
                correlationId,
                membership.updatedAt()));
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected || expected < 1) {
            throw new OrganizationDomainException(
                    OrganizationReasonCode.VERSION_CONFLICT,
                    "Expected version " + expected + " but was " + actual
            );
        }
    }
}
