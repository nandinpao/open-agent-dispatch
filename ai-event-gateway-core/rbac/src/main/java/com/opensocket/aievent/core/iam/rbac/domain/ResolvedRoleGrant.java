package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Objects;
public record ResolvedRoleGrant(PrincipalRoleBinding binding,Role role,Permission permission){public ResolvedRoleGrant{Objects.requireNonNull(binding,"binding");Objects.requireNonNull(role,"role");Objects.requireNonNull(permission,"permission");}}
