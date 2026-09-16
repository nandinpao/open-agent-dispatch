package com.opensocket.aievent.core.iam.api.request;
import com.opensocket.aievent.core.iam.organization.domain.GroupStatus;import jakarta.validation.constraints.*;
public record ChangeGroupStatusRequest(@NotNull GroupStatus status,@NotBlank @Size(max=500) String reason) { }
