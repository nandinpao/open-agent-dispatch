package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;import java.util.Set;
public record ReplaceRolePermissionsRequest(@NotNull Set<String> permissionCodes,String approvalId) {
 public ReplaceRolePermissionsRequest { permissionCodes=permissionCodes==null?Set.of():Set.copyOf(permissionCodes);approvalId=approvalId==null||approvalId.isBlank()?null:approvalId.trim(); }
}
