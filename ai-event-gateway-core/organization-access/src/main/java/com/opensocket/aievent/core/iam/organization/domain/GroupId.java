package com.opensocket.aievent.core.iam.organization.domain;

public record GroupId(String value) {
    public GroupId { value = OrganizationText.required(value, "groupId", 128); }
}
