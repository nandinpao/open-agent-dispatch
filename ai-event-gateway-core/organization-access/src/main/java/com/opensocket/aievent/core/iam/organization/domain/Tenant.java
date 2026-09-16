package com.opensocket.aievent.core.iam.organization.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Tenant {
    private static final Map<TenantStatus, Set<TenantStatus>> ALLOWED = transitions();
    private final TenantId tenantId; private final String tenantCode; private final String tenantName;
    private final String legalName; private final TenantStatus status; private final ZoneId defaultTimezone;
    private final Locale defaultLocale; private final String dataRegion; private final Instant createdAt;
    private final Instant updatedAt; private final String updatedBy; private final long version;

    private Tenant(TenantId tenantId, String tenantCode, String tenantName, String legalName, TenantStatus status,
                   ZoneId defaultTimezone, Locale defaultLocale, String dataRegion, Instant createdAt,
                   Instant updatedAt, String updatedBy, long version) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.tenantCode = OrganizationText.required(tenantCode, "tenantCode", 64);
        this.tenantName = OrganizationText.required(tenantName, "tenantName", 200);
        this.legalName = OrganizationText.optional(legalName, 300);
        this.status = Objects.requireNonNull(status, "status");
        this.defaultTimezone = Objects.requireNonNull(defaultTimezone, "defaultTimezone");
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
        this.dataRegion = OrganizationText.required(dataRegion, "dataRegion", 64);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        this.updatedBy = OrganizationText.required(updatedBy, "updatedBy", 128);
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
    }

    public static Tenant provision(TenantId tenantId, String tenantCode, String tenantName, String legalName,
                                   ZoneId timezone, Locale locale, String dataRegion, String actorId, Instant at) {
        return new Tenant(tenantId, tenantCode, tenantName, legalName, TenantStatus.PROVISIONING, timezone, locale,
                dataRegion, at, at, actorId, 1);
    }

    public static Tenant reconstitute(TenantId tenantId, String tenantCode, String tenantName, String legalName,
                                      TenantStatus status, ZoneId timezone, Locale locale, String dataRegion,
                                      Instant createdAt, Instant updatedAt, String updatedBy, long version) {
        return new Tenant(tenantId, tenantCode, tenantName, legalName, status, timezone, locale, dataRegion,
                createdAt, updatedAt, updatedBy, version);
    }

    public Tenant changeStatus(TenantStatus target, String actorId, Instant at) {
        Objects.requireNonNull(target, "target");
        if (target == status) return this;
        if (!ALLOWED.getOrDefault(status, Set.of()).contains(target)) {
            throw new OrganizationDomainException(OrganizationReasonCode.INVALID_STATUS_TRANSITION,
                    "Tenant status transition " + status + " -> " + target + " is not allowed");
        }
        return new Tenant(tenantId, tenantCode, tenantName, legalName, target, defaultTimezone, defaultLocale,
                dataRegion, createdAt, at, actorId, version + 1);
    }

    public Tenant updateProfile(String name, String legal, ZoneId timezone, Locale locale, String region,
                                String actorId, Instant at) {
        if (status == TenantStatus.DECOMMISSIONING) throw new IllegalStateException("decommissioning tenant cannot be edited");
        return new Tenant(tenantId, tenantCode, name, legal, status, timezone, locale, region, createdAt, at, actorId, version + 1);
    }

    public TenantId tenantId() { return tenantId; } public String tenantCode() { return tenantCode; }
    public String tenantName() { return tenantName; } public String legalName() { return legalName; }
    public TenantStatus status() { return status; } public ZoneId defaultTimezone() { return defaultTimezone; }
    public Locale defaultLocale() { return defaultLocale; } public String dataRegion() { return dataRegion; }
    public Instant createdAt() { return createdAt; } public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; } public long version() { return version; }

    private static Map<TenantStatus, Set<TenantStatus>> transitions() {
        EnumMap<TenantStatus, Set<TenantStatus>> m = new EnumMap<>(TenantStatus.class);
        m.put(TenantStatus.PROVISIONING, EnumSet.of(TenantStatus.ACTIVE, TenantStatus.DISABLED));
        m.put(TenantStatus.ACTIVE, EnumSet.of(TenantStatus.SUSPENDED, TenantStatus.DISABLED, TenantStatus.DECOMMISSIONING));
        m.put(TenantStatus.SUSPENDED, EnumSet.of(TenantStatus.ACTIVE, TenantStatus.DISABLED, TenantStatus.DECOMMISSIONING));
        m.put(TenantStatus.DISABLED, EnumSet.of(TenantStatus.ACTIVE, TenantStatus.DECOMMISSIONING));
        m.put(TenantStatus.DECOMMISSIONING, EnumSet.noneOf(TenantStatus.class));
        return Map.copyOf(m);
    }
}
