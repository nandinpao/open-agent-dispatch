package com.opensocket.aievent.gateway.netty.common.dto;

public record ErrorPayload(String code, String message, String detail) {
}
