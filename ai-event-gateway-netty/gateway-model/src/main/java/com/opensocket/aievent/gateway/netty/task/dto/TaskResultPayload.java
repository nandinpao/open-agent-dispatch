package com.opensocket.aievent.gateway.netty.task.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record TaskResultPayload(@NotBlank String taskId, @NotBlank String agentId, Map<String, Object> result) {
}
