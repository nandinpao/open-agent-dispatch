package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.service.IamRbacAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamRbacHardeningService;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.pagination.*;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.api.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/access/platform")
@ConditionalOnBean({IamRbacAdministrationService.class,IamRbacHardeningService.class,
        IamAdministrationProjectionPort.class,IamPermissionGuard.class,R7SensitiveOperationGuard.class})
@ConditionalOnProperty(prefix="aeg.iam.api",name="enabled",havingValue="true")
public class IamPlatformRoleController {
    private final IamRbacAdministrationService service;private final IamAdministrationProjectionPort projections;
    private final IamPermissionGuard guard;private final IamApiRequestContextFactory contexts;private final IamPaginationPolicy pagination;
    private final IamRbacHardeningService hardening;private final R7SensitiveOperationGuard sensitiveWrites;
    public IamPlatformRoleController(IamRbacAdministrationService service,IamRbacHardeningService hardening,
            IamAdministrationProjectionPort projections,IamPermissionGuard guard,R7SensitiveOperationGuard sensitiveWrites,
            IamApiRequestContextFactory contexts,IamPaginationPolicy pagination){this.service=service;this.hardening=hardening;
        this.projections=projections;this.guard=guard;this.sensitiveWrites=sensitiveWrites;this.contexts=contexts;this.pagination=pagination;}

    @GetMapping("/roles")
    public OffsetPage<RoleResponse> roles(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String text,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String status,HttpServletRequest request){var context=contexts.from(request);guard.requireInstance(context,IamPermissions.PLATFORM_ROLE_READ,"ROLE","");return projections.platformRoles(pagination.page(page),pagination.size(size),text,type,status);}

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest body,HttpServletRequest request){var context=contexts.from(request);sensitiveWrites.require(context);guard.requireInstance(context,IamPermissions.PLATFORM_ROLE_MANAGE,"ROLE",body.authorizationTarget());return ResponseEntity.status(201).body(service.createPlatform(body,context));}

    @PutMapping("/roles/{roleId}")
    public RoleResponse update(@PathVariable String roleId,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody UpdateRoleRequest body,HttpServletRequest request){var context=contexts.from(request);sensitiveWrites.require(context);guard.requireInstance(context,IamPermissions.PLATFORM_ROLE_MANAGE,"ROLE",roleId);return service.updatePlatform(roleId,body,context.requireExpectedVersion(ifMatch),context);}

    @PostMapping("/roles/{roleId}/status")
    public RoleResponse status(@PathVariable String roleId,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody ChangeRoleStatusRequest body,HttpServletRequest request){var context=contexts.from(request);sensitiveWrites.require(context);guard.requireInstance(context,IamPermissions.PLATFORM_ROLE_MANAGE,"ROLE",roleId);return service.changePlatformStatus(roleId,body,context.requireExpectedVersion(ifMatch),context);}

    @GetMapping("/roles/{roleId}/permissions")
    public RolePermissionMatrixResponse rolePermissions(@PathVariable String roleId,HttpServletRequest request){var context=contexts.from(request);guard.requireInstance(context,IamPermissions.PLATFORM_ROLE_READ,"ROLE",roleId);return new RolePermissionMatrixResponse(roleId,projections.rolePermissions("",roleId));}

    @PutMapping("/roles/{roleId}/permissions")
    public ResponseEntity<Void> replacePermissions(@PathVariable String roleId,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody ReplaceRolePermissionsRequest body,HttpServletRequest request){var context=contexts.from(request);sensitiveWrites.require(context);guard.requireInstance(context,IamPermissions.ROLE_PERMISSION_MANAGE,"ROLE",roleId);service.replacePlatformPermissions(roleId,body,context.requireExpectedVersion(ifMatch),context);return ResponseEntity.noContent().build();}

    @PostMapping("/roles/{roleId}/permissions/hardening-preview")
    public RbacHardeningPreviewResponse previewRolePermissionHardening(@PathVariable String roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest body,HttpServletRequest request){
        var context=contexts.from(request);guard.requireInstance(context,IamPermissions.PLATFORM_ROLE_READ,"ROLE",roleId);
        return hardening.previewPermissionReplacement("",roleId,body,context);
    }

    @PostMapping("/roles/{roleId}/permissions/approval-requests")
    public ResponseEntity<RbacCriticalApprovalResponse> requestRolePermissionApproval(@PathVariable String roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest body,HttpServletRequest request){
        var context=contexts.from(request);sensitiveWrites.require(context);
        guard.requireInstance(context,IamPermissions.ROLE_APPROVAL_REQUEST,"ROLE",roleId);
        return ResponseEntity.status(201).body(hardening.requestPermissionApproval("",roleId,body,context));
    }

    @PostMapping("/role-bindings/hardening-preview")
    public RbacHardeningPreviewResponse previewRoleBindingHardening(@Valid @RequestBody BindRoleRequest body,HttpServletRequest request){
        var context=contexts.from(request);guard.requireInstance(context,IamPermissions.ROLE_BINDING_READ,"ROLE_BINDING",body.authorizationTarget());
        return hardening.previewBinding("",body,context);
    }

    @PostMapping("/role-bindings/approval-requests")
    public ResponseEntity<RbacCriticalApprovalResponse> requestRoleBindingApproval(@Valid @RequestBody BindRoleRequest body,HttpServletRequest request){
        var context=contexts.from(request);sensitiveWrites.require(context);
        guard.requireInstance(context,IamPermissions.ROLE_APPROVAL_REQUEST,"ROLE_BINDING",body.authorizationTarget());
        return ResponseEntity.status(201).body(hardening.requestBindingApproval("",body,context));
    }

    @GetMapping("/rbac-approvals")
    public java.util.List<RbacCriticalApprovalResponse> rbacApprovals(@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="50") int limit,HttpServletRequest request){
        var context=contexts.from(request);guard.requireInstance(context,IamPermissions.ROLE_APPROVAL_READ,"RBAC_APPROVAL","");
        return hardening.approvals("",status,limit);
    }

    @GetMapping("/rbac-approvals/{approvalId}")
    public RbacCriticalApprovalResponse rbacApproval(@PathVariable String approvalId,HttpServletRequest request){
        var context=contexts.from(request);guard.requireInstance(context,IamPermissions.ROLE_APPROVAL_READ,"RBAC_APPROVAL",approvalId);
        return hardening.approval("",approvalId);
    }

    @PostMapping("/rbac-approvals/{approvalId}/approve")
    public RbacCriticalApprovalResponse approveRbacChange(@PathVariable String approvalId,
            @Valid @RequestBody RbacApprovalDecisionRequest body,HttpServletRequest request){
        var context=contexts.from(request);sensitiveWrites.require(context);
        guard.requireInstance(context,IamPermissions.ROLE_APPROVAL_APPROVE,"RBAC_APPROVAL",approvalId);
        return hardening.approve("",approvalId,body.reason(),context);
    }

    @PostMapping("/rbac-approvals/{approvalId}/reject")
    public RbacCriticalApprovalResponse rejectRbacChange(@PathVariable String approvalId,
            @Valid @RequestBody RbacApprovalDecisionRequest body,HttpServletRequest request){
        var context=contexts.from(request);sensitiveWrites.require(context);
        guard.requireInstance(context,IamPermissions.ROLE_APPROVAL_APPROVE,"RBAC_APPROVAL",approvalId);
        return hardening.reject("",approvalId,body.reason(),context);
    }

    @GetMapping("/permissions")
    public OffsetPage<PermissionResponse> permissions(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String text,HttpServletRequest request){var context=contexts.from(request);guard.requireInstance(context,IamPermissions.PLATFORM_ROLE_READ,"PERMISSION","");return projections.permissions(pagination.page(page),pagination.size(size),text,"INSTANCE");}

    @GetMapping("/role-bindings")
    public CursorPage<RoleBindingResponse> bindings(@RequestParam(defaultValue="") String roleId,@RequestParam(defaultValue="") String principalId,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor,HttpServletRequest request){var context=contexts.from(request);guard.requireInstance(context,IamPermissions.ROLE_BINDING_READ,"ROLE_BINDING","");return projections.roleBindings("",roleId,principalId,pagination.limit(limit),cursor);}

    @PostMapping("/role-bindings")
    public ResponseEntity<RoleBindingResponse> bind(@Valid @RequestBody BindRoleRequest body,HttpServletRequest request){var context=contexts.from(request);sensitiveWrites.require(context);guard.requireInstance(context,IamPermissions.ROLE_BINDING_MANAGE,"ROLE_BINDING",body.authorizationTarget());return ResponseEntity.status(201).body(service.bindPlatform(body,context));}

    @PostMapping("/role-bindings/{bindingId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable String bindingId,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody RevokeBindingRequest body,HttpServletRequest request){var context=contexts.from(request);sensitiveWrites.require(context);guard.requireInstance(context,IamPermissions.ROLE_BINDING_MANAGE,"ROLE_BINDING",bindingId);service.revokePlatformBinding(bindingId,body,context.requireExpectedVersion(ifMatch),context);return ResponseEntity.noContent().build();}
}
