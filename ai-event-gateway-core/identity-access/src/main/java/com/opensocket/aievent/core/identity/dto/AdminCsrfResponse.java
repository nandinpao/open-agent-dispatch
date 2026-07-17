package com.opensocket.aievent.core.identity.dto;

public record AdminCsrfResponse(String headerName, String parameterName, String token) {
}
