package com.opensocket.aievent.core.iam.rbac.application.port.in;
import com.opensocket.aievent.core.iam.rbac.application.command.*;
import com.opensocket.aievent.core.iam.rbac.domain.*;
public interface RbacAdministrationPort {
    Role createRole(CreateRoleCommand command);
    Role updateRole(UpdateRoleCommand command);
    Role changeRoleStatus(ChangeRoleStatusCommand command);
    void replacePermissions(ReplaceRolePermissionsCommand command);
    Role resolveRoleByCode(String tenantId, String roleCode);
    PrincipalRoleBinding bindRole(BindRoleCommand command);
    PrincipalRoleBinding changeBinding(ChangeRoleBindingCommand command);
    void revokeBinding(RevokeRoleBindingCommand command);
}
