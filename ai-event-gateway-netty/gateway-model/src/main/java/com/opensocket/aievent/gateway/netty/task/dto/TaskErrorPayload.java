package com.opensocket.aievent.gateway.netty.task.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskErrorPayload(@NotBlank String taskId, @NotBlank String agentId, String errorCode, String errorMessage) {
}
