package com.opensocket.aievent.core.action.executor.issue;

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
public class IssueExecutorResponse {
    private boolean success;
    private String responseRef;
    private String issueId;
    private String issueUrl;
    private String issueStatus;
    private String vendor;
    private String commentId;
    private String error;
    private boolean retryable;
    private Integer statusCode;
}

