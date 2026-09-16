package com.opensocket.aievent.core.iam.api.response;
import java.util.List;
public record RolePermissionMatrixResponse(String roleId,List<PermissionResponse> permissions) { }
