package com.opensocket.aievent.gateway.netty.task.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskAckPayload(@NotBlank String taskId, @NotBlank String agentId) {
}
