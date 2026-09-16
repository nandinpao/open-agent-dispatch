package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;
public record UpdateDepartmentRequest(@NotBlank String code,@NotBlank String name,String parentDepartmentId,String managerUserId,@Min(0) int displayOrder,@NotBlank @Size(max=500) String reason) { }
