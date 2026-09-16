package com.opensocket.aievent.core.iam.organization.domain;

public record MembershipId(String value) {
    public MembershipId { value = OrganizationText.required(value, "membershipId", 128); }
}
