package com.opensocket.aievent.core.iam.api.request;
import com.opensocket.aievent.core.iam.organization.domain.GroupType;import jakarta.validation.constraints.*;
public record UpdateGroupRequest(@NotBlank String code,@NotBlank String name,@NotNull GroupType type,String parentGroupId,String ownerDepartmentId,@Size(max=1000) String description,@NotBlank @Size(max=500) String reason) { }
