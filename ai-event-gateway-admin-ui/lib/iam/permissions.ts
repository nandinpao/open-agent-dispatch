export const iamPermissions = {
  usersRead: 'identity.user.read', usersCreate: 'identity.user.create', usersUpdate: 'identity.user.update',
  platformUsersRead: 'identity.platform_user.read', platformUsersCreate: 'identity.platform_user.create', platformUsersUpdate: 'identity.platform_user.update', platformUsersSecurity: 'identity.platform_user.security',
  tenantMembershipsRead: 'identity.tenant_membership.read', tenantMembershipsManage: 'identity.tenant_membership.manage',
  departmentsRead: 'identity.department.read', departmentsManage: 'identity.department.manage',
  groupsRead: 'identity.group.read', groupsManage: 'identity.group.manage',
  platformRolesRead: 'identity.platform_role.read', platformRolesManage: 'identity.platform_role.manage',
  permissionCatalogRead: 'permission.catalog.read', permissionCatalogManage: 'permission.catalog.manage', permissionCatalogPublish: 'permission.catalog.publish',
  tenantRolesRead: 'identity.tenant_role.read', tenantRolesManage: 'identity.tenant_role.manage',
  rolePermissionsManage: 'identity.role_permission.manage', roleBindingsRead: 'identity.role_binding.read', roleBindingsManage: 'identity.role_binding.manage', roleAccessReviewManage: 'identity.role_access_review.manage',
  membershipManage: 'identity.membership.manage',
  sessionsRead: 'security.session.read', sessionsRevoke: 'security.session.revoke', mfaReset: 'security.mfa.reset',
  tokensRead: 'security.token.read', tokensManage: 'security.token.manage',
  policyRead: 'security.policy.read', policyManage: 'security.policy.manage',
  auditRead: 'audit.identity.read', tenantRead: 'instance.tenant.read', tenantManage: 'instance.tenant.manage'
} as const;
