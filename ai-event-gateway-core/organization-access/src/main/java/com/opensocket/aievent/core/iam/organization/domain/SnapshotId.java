package com.opensocket.aievent.core.iam.organization.domain;

public record SnapshotId(String value) {
    public SnapshotId { value = OrganizationText.required(value, "snapshotId", 128); }
}
