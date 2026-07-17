package com.opensocket.aievent.core.identity.dto;

import java.util.Set;

public record AdminPermissionsResponse(Set<String> roles, Set<String> permissions) {
}
