package com.opensocket.aievent.core.action.executor.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpExecutorResponse {
    private boolean success;
    private String responseRef;
    private String error;
}
