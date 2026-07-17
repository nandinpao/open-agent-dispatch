package com.opensocket.aievent.core.identity.dto;

import java.util.List;

public record AdminSecurityAuditResponse(List<AdminSecurityAuditEvent> events) {
    public AdminSecurityAuditResponse { events = events == null ? List.of() : List.copyOf(events); }
}
