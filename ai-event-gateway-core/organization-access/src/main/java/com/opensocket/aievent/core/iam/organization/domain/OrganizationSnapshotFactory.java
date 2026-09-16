package com.opensocket.aievent.core.iam.organization.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

public final class OrganizationSnapshotFactory {
    public OrganizationSnapshot capture(SnapshotId snapshotId, DepartmentRevision revision, Set<GroupId> groups, Instant capturedAt) {
        String canonical = String.join("|", revision.tenantId().value(), revision.departmentId().value(), Long.toString(revision.revision()),
                revision.departmentCode(), revision.departmentName(),
                revision.ancestorPathIds().stream().map(DepartmentId::value).collect(Collectors.joining("/")),
                revision.ancestorPathCodes().stream().collect(Collectors.joining("/")),
                revision.ancestorPathNames().stream().collect(Collectors.joining("/")),
                groups == null ? "" : groups.stream().map(GroupId::value).sorted(Comparator.naturalOrder()).collect(Collectors.joining(",")));
        return new OrganizationSnapshot(snapshotId, revision.tenantId(), revision.departmentId(), revision.revision(),
                revision.departmentCode(), revision.departmentName(), revision.ancestorPathIds(), revision.ancestorPathCodes(),
                revision.ancestorPathNames(), groups, capturedAt, sha256(canonical));
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }
}
