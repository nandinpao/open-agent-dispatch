package com.opensocket.aievent.core.iam.api.request;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentStatus;import jakarta.validation.constraints.*;
public record ChangeDepartmentStatusRequest(@NotNull DepartmentStatus status,@NotBlank @Size(max=500) String reason) { }
