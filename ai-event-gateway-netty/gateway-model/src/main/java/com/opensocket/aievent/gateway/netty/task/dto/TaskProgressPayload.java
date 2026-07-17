package com.opensocket.aievent.gateway.netty.task.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskProgressPayload(@NotBlank String taskId, @NotBlank String agentId, Integer progressPercent, String message) {
}
