package com.opensocket.aievent.core.identity.dto;

import java.time.OffsetDateTime;

public record AdminLogoutResponse(String status, OffsetDateTime loggedOutAt) {
}
