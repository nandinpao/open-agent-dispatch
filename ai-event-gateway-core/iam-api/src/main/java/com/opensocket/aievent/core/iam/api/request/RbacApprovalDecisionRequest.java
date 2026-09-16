package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.NotBlank;
public record RbacApprovalDecisionRequest(@NotBlank String reason) { public RbacApprovalDecisionRequest { reason=reason==null?null:reason.trim(); } }
