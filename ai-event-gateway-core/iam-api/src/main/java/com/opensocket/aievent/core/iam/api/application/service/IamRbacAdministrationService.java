package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.rbac.application.command.*;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;

public final class IamRbacAdministrationService {
    private final RbacAdministrationPort port;
    private final IamIdempotencyExecutor idempotency;
    private final IamRbacHardeningService hardening;
    public IamRbacAdministrationService(RbacAdministrationPort port,IamIdempotencyExecutor idempotency,IamRbacHardeningService hardening){this.port=port;this.idempotency=idempotency;this.hardening=hardening;}

    public RoleResponse createTenant(CreateRoleRequest request,IamApiRequestContext context){return create(request,context,context.activeTenantId(),false);}
    public RoleResponse createPlatform(CreateRoleRequest request,IamApiRequestContext context){return create(request,context,"",true);}
    private RoleResponse create(CreateRoleRequest request,IamApiRequestContext context,String tenantId,boolean platform){
        String key=context.requireIdempotencyKey(),scope=platform?"INSTANCE":tenantId,reason=context.requireAuditReason();
        String operation=platform?"identity.platform_role.create":"identity.tenant_role.create";
        String roleId=request.roleId()==null||request.roleId().isBlank()
                ? IamOperationIds.resourceId("role",operation,scope,key):request.roleId();
        return idempotency.execute(scope,context.actorId(),operation,key,request,201,RoleResponse.class,
                ()->RoleResponse.from(port.createRole(new CreateRoleCommand(roleId,tenantId,platform,request.roleCode(),request.roleName(),request.description(),context.actorId(),reason,context.requestedAt()))));
    }

    public RoleResponse updateTenant(String roleId,UpdateRoleRequest request,long version,IamApiRequestContext context){return update(roleId,request,version,context,context.activeTenantId(),false);}
    public RoleResponse updatePlatform(String roleId,UpdateRoleRequest request,long version,IamApiRequestContext context){return update(roleId,request,version,context,"",true);}
    private RoleResponse update(String roleId,UpdateRoleRequest request,long version,IamApiRequestContext context,String tenantId,boolean platform){
        String key=context.requireIdempotencyKey(),scope=platform?"INSTANCE":tenantId,reason=context.requireAuditReason();
        return idempotency.execute(scope,context.actorId(),platform?"identity.platform_role.update":"identity.tenant_role.update",key,request,200,RoleResponse.class,
                ()->RoleResponse.from(port.updateRole(new UpdateRoleCommand(tenantId,roleId,request.roleName(),request.description(),context.actorId(),reason,context.requestedAt(),version))));
    }

    public RoleResponse changeTenantStatus(String roleId,ChangeRoleStatusRequest request,long version,IamApiRequestContext context){return changeStatus(roleId,request,version,context,context.activeTenantId(),false);}
    public RoleResponse changePlatformStatus(String roleId,ChangeRoleStatusRequest request,long version,IamApiRequestContext context){return changeStatus(roleId,request,version,context,"",true);}
    private RoleResponse changeStatus(String roleId,ChangeRoleStatusRequest request,long version,IamApiRequestContext context,String tenantId,boolean platform){
        String key=context.requireIdempotencyKey(),scope=platform?"INSTANCE":tenantId,reason=context.requireAuditReason();
        return idempotency.execute(scope,context.actorId(),platform?"identity.platform_role.status":"identity.tenant_role.status",key,request,200,RoleResponse.class,
                ()->RoleResponse.from(port.changeRoleStatus(new ChangeRoleStatusCommand(tenantId,roleId,request.status(),context.actorId(),reason,context.requestedAt(),version))));
    }

    public void replaceTenantPermissions(String roleId,ReplaceRolePermissionsRequest request,long expectedVersion,IamApiRequestContext context){replacePermissions(roleId,request,expectedVersion,context,context.activeTenantId(),false);}
    public void replacePlatformPermissions(String roleId,ReplaceRolePermissionsRequest request,long expectedVersion,IamApiRequestContext context){replacePermissions(roleId,request,expectedVersion,context,"",true);}
    private void replacePermissions(String roleId,ReplaceRolePermissionsRequest request,long expectedVersion,IamApiRequestContext context,String tenantId,boolean platform){
        String key=context.requireIdempotencyKey(),scope=platform?"INSTANCE":tenantId,reason=context.requireAuditReason();
        var assessment=hardening.enforcePermissionReplacement(tenantId,roleId,request,context);
        idempotency.execute(scope,context.actorId(),platform?"identity.platform_role.permission.replace":"identity.tenant_role.permission.replace",key,request,204,String.class,()->{hardening.consumeApprovalForMutation(tenantId,request.approvalId(),com.opensocket.aievent.core.iam.rbac.domain.RbacApprovalOperation.ROLE_PERMISSION_REPLACE,assessment,context);port.replacePermissions(new ReplaceRolePermissionsCommand(tenantId,roleId,request.permissionCodes(),context.actorId(),reason,context.requestedAt(),expectedVersion));hardening.recordEvidence(tenantId,"ROLE",roleId,assessment,request.approvalId(),context);return "OK";});
    }

    public RoleBindingResponse bindTenant(BindRoleRequest request,IamApiRequestContext context){return bind(request,context,context.activeTenantId(),false);}
    public RoleBindingResponse bindPlatform(BindRoleRequest request,IamApiRequestContext context){return bind(request,context,"",true);}
    private RoleBindingResponse bind(BindRoleRequest request,IamApiRequestContext context,String tenantId,boolean platform){
        String key=context.requireIdempotencyKey(),scope=platform?"INSTANCE":tenantId,reason=context.requireAuditReason();
        String operation=platform?"identity.platform_role.binding.create":"identity.tenant_role.binding.create";
        String bindingId=request.bindingId()==null||request.bindingId().isBlank()
                ? IamOperationIds.resourceId("rb",operation,scope+"|"+request.principalId()+"|"+request.roleId(),key):request.bindingId();
        Instant effective=request.effectiveAt()==null?context.requestedAt():request.effectiveAt();
        var assessment=hardening.enforceBinding(tenantId,request,context);
        return idempotency.execute(scope,context.actorId(),operation,key,request,201,RoleBindingResponse.class,
                ()->{hardening.consumeApprovalForMutation(tenantId,request.approvalId(),com.opensocket.aievent.core.iam.rbac.domain.RbacApprovalOperation.ROLE_BINDING_CREATE,assessment,context);var response=RoleBindingResponse.from(port.bindRole(new BindRoleCommand(bindingId,tenantId,new PrincipalRef(request.principalType(),request.principalId()),request.roleId(),request.scopeType(),request.scopeId(),effective,request.expiresAt(),context.actorId(),reason,context.requestedAt())));hardening.recordEvidence(tenantId,"ROLE_BINDING",bindingId,assessment,request.approvalId(),context);return response;});
    }

    public void revokeTenantBinding(String bindingId,RevokeBindingRequest request,long version,IamApiRequestContext context){revokeBinding(bindingId,request,version,context,context.activeTenantId(),false);}
    public void revokePlatformBinding(String bindingId,RevokeBindingRequest request,long version,IamApiRequestContext context){revokeBinding(bindingId,request,version,context,"",true);}
    private void revokeBinding(String bindingId,RevokeBindingRequest request,long version,IamApiRequestContext context,String tenantId,boolean platform){
        String key=context.requireIdempotencyKey(),scope=platform?"INSTANCE":tenantId;context.requireAuditReason();
        idempotency.execute(scope,context.actorId(),platform?"identity.platform_role.binding.revoke":"identity.tenant_role.binding.revoke",key,request,204,String.class,()->{port.revokeBinding(new RevokeRoleBindingCommand(tenantId,bindingId,context.actorId(),request.reason(),context.requestedAt(),version));return "OK";});
    }
}
