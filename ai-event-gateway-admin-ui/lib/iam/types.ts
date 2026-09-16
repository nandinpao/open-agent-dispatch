import type { UiEntitlementResponse } from '@/lib/navigation/uiEntitlements';
export interface IamApiError { errorCode?: string; error_code?: string; message: string; correlationId?: string; correlation_id?: string; requiredPermission?: string; activeTenantId?: string; fieldErrors?: unknown[]; }
export interface CursorPage<T> { items: T[]; nextCursor: string; hasMore: boolean; }
export interface OffsetPage<T> { items: T[]; page: number; size: number; hasMore: boolean; totalCount?: number | null; }
export interface TenantChoice { tenantId: string; tenantCode: string; tenantName: string; membershipStatus: string; roleSummary: string[]; }
export interface IamUiSession { authenticationType: 'CANONICAL_SESSION'; userId: string; username: string; displayName: string; roles: string[]; permissions: string[]; permissionScopes: Record<string, string[]>; selectedTenantId: string; tenantChoices: TenantChoice[]; requiredActions: string[]; credentialVersion: number; authenticatedAt: string; expiresAt: string; authenticationMethods: string[]; }
export interface IamLoginResponse { state: string; challengeId?: string; session?: unknown; tenantChoices: TenantChoice[]; requiredActions: string[]; credentialVersion: number; }
export interface BootstrapStatus { status: string; version: number; completedSteps: string[]; bootstrapApiOpen: boolean; }
export interface MfaEnrollment { methodId: string; provisioningUri: string; secretDisplay: string; recoveryCodes: string[]; expectedVersion: number; }
export interface Tenant { tenantId: string; tenantCode: string; tenantName: string; legalName: string; status: string; timezone: string; locale: string; dataRegion: string; createdAt: string; updatedAt: string; version: number; }
export interface TenantWorkspaceSummary { tenantId: string; activePeople: number; tenantAdministratorCount: number; pendingInvitations: number; signInSetupRequiredCount: number; mfaEnrolledPeople: number; suspendedPeople: number; peopleWithoutDepartment: number; departmentCount: number; departmentsWithoutManager: number; groupCount: number; activeRoleCount: number; activeBindingCount: number; expiringBindingCount: number; }
export interface PlatformTenantMembership { userId: string; tenantId: string; tenantCode: string; tenantName: string; membershipStatus: string; tenantStatus: string; expiresAt?: string; defaultTenant: boolean; membershipSource: string; membershipVersion: number; updatedAt: string; }
export interface User { userId: string; username: string; email: string; displayName: string; status: string; creationMode: string; createdAt: string; updatedAt: string; version: number; authenticationMethod: string; signInState: 'READY'|'SSO_MANAGED'|'SSO_LINK_REQUIRED'|'SETUP_REQUIRED'|'MFA_REQUIRED'|'LOCKED'|'SUSPENDED'|'DISABLED'|string; passwordState: string; mfaState: string; deliveryStatus: string; accessReadiness: 'READY'|'NO_RESPONSIBILITY'|'NO_EFFECTIVE_PERMISSION'|string; responsibilitySummary: string; }

export interface AuthenticationProvider { tenantId: string; providerId: string; providerCode: string; providerType: 'OIDC'|'SAML'|string; displayName: string; status: 'ACTIVE'|'DISABLED'|string; issuerUri: string; clientId: string; clientSecretRef: string; scopes: string[]; subjectClaim: string; usernameClaim: string; emailClaim: string; displayNameClaim: string; amrClaim: string; acrClaim: string; upstreamMfaMode: 'NONE'|'TRUST_AMR'|'TRUST_ACR'|'TRUST_AMR_OR_ACR'|'REQUIRE_ASSERTED'|string; trustedAmrValues: string[]; trustedAcrValues: string[]; linkMode: 'EXPLICIT_ONLY'|'VERIFIED_EMAIL_EXISTING_USER'|string; jitMode: 'DISABLED'|'CREATE_ACTIVE_SSO_USER'|string; authorizationEnabled: boolean; version: number; }
export interface FederationPolicy { tenantId: string; localLoginEnabled: boolean; oidcLoginEnabled: boolean; samlLoginEnabled: boolean; providerDiscoveryEnabled: boolean; defaultProviderId: string; version: number; }
export interface ExternalIdentityLink { credentialLinkId: string; tenantId: string; providerId: string; providerCode: string; providerType: string; issuerUri: string; externalSubject: string; upstreamUsername: string; upstreamEmail: string; upstreamEmailVerified?: boolean | null; userId: string; canonicalUsername: string; status: string; lastAuthenticatedAt?: string | null; version: number; }
export interface FederationProviderPublic { tenantId: string; providerId: string; providerCode: string; providerType: string; displayName: string; defaultProvider: boolean; upstreamMfaRequired: boolean; }
export interface OidcStartResponse { authorizationUrl: string; providerId: string; providerDisplayName: string; expiresAt: string; }
export interface UserInvitationStatus { userId: string; status: 'NOT_ISSUED'|'PENDING'|'ACCEPTED'|'EXPIRED'|'REVOKED'|string; deliveryReference: string; tokenId: string; issuedAt?: string | null; expiresAt?: string | null; canResend: boolean; canRevoke: boolean; deliveryMethod: string; deliveryStatus: string; deliveryFailureCode: string; setupActionUrl: string; }
export interface UserOnboardingResult { user: User; tenantMembership: Membership; departmentMemberships: Membership[]; groupMemberships: Membership[]; roleBindings: RoleBinding[]; invitationIssued: boolean; setupIssued: boolean; temporaryPasswordConfigured: boolean; authenticationMethod: string; setupDeliveryMethod: string; setupDeliveryStatus: string; setupDeliveryReference: string; setupDeliveryId: string; setupExpiresAt?: string | null; setupFailureCode: string; setupActionUrl: string; requiredActions: string[]; }
export interface UserOnboardingDepartmentAssignment { membershipId: string; departmentId: string; membershipType: 'MEMBER'|'DELEGATE'; primary: boolean; expiresAt?: string | null; }
export interface UserOnboardingGroupAssignment { membershipId: string; groupId: string; membershipRole: 'MEMBER'|'LEAD'; expiresAt?: string | null; }
export interface UserOnboardingRoleAssignment { bindingId: string; roleId: string; scopeType: 'TENANT'|'DEPARTMENT'|'DEPARTMENT_SUBTREE'|'GROUP'; scopeId: string; effectiveAt?: string | null; expiresAt?: string | null; }
export interface UserOnboardingRequest {
  userId?: string | null;
  username: string;
  email?: string | null;
  displayName: string;
  creationMode: 'ADMIN_CREATED'|'INVITATION'|'LEGACY_IMPORT';
  authenticationMethod: 'LOCAL';
  activationDeliveryMethod: 'EMAIL'|'MANUAL'|'DEVELOPMENT_FILE';
  initialPassword?: string | null;
  membershipId: string;
  membershipStatus: 'INVITED'|'ACTIVE';
  employeeId?: string | null;
  membershipExpiresAt?: string | null;
  defaultTenant: boolean;
  membershipSource: 'ADMIN_CREATED'|'INVITATION'|'LEGACY_IMPORT'|'PLATFORM_PROVISIONING';
  departments: UserOnboardingDepartmentAssignment[];
  groups: UserOnboardingGroupAssignment[];
  roles: UserOnboardingRoleAssignment[];
  applicationAccessDeferred: boolean;
  reason: string;
}
export interface Department { tenantId: string; departmentId: string; code: string; name: string; parentDepartmentId: string; managerUserId: string; status: string; displayOrder: number; createdAt: string; updatedAt: string; version: number; }
export interface Group { tenantId: string; groupId: string; code: string; name: string; type: string; parentGroupId: string; ownerDepartmentId: string; status: string; description: string; createdAt: string; updatedAt: string; version: number; }
export interface OrganizationRetirementPreview { tenantId: string; organizationType: 'DEPARTMENT'|'GROUP'|string; organizationId: string; organizationName: string; peopleCount: number; primaryPeopleCount: number; childCount: number; ownedGroupCount: number; activeRoleBindingCount: number; sourceSystemCount: number; dispatchFlowCount: number; agentPoolCount: number; a2aPolicyCount: number; hardBlockerCount: number; canDelete: boolean; }
export interface Membership { membershipId: string; membershipType: string; tenantId: string; userId: string; resourceId: string; role: string; status: string; primary: boolean; effectiveAt: string; expiresAt?: string; version: number; employeeId?: string; }
export type PeopleBulkOperation = 'MOVE_PRIMARY_DEPARTMENT'|'ADD_DEPARTMENT_MEMBERSHIP'|'ADD_GROUPS'|'REMOVE_GROUPS'|'SUSPEND'|'REACTIVATE'|'REVOKE_SESSIONS'|'RESEND_INVITATION';
export interface PeopleBulkActionRequest { operation: PeopleBulkOperation; userIds: string[]; targetDepartmentId?: string | null; groupIds?: string[]; membershipRole?: 'MEMBER'|'LEAD'; deliveryMethod?: 'EMAIL'|'MANUAL'|'DEVELOPMENT_FILE'; }
export interface PeopleBulkItemResult { userId: string; displayName: string; outcome: 'SUCCEEDED'|'SKIPPED'|'FAILED'|string; changedCount: number; code: string; message: string; remediation: string; }
export interface PeopleBulkActionResult { bulkOperationId: string; operation: PeopleBulkOperation|string; executionPolicy: 'PARTIAL_SUCCESS'|string; selectedCount: number; succeededCount: number; skippedCount: number; failedCount: number; changedCount: number; results: PeopleBulkItemResult[]; }
export interface Role { roleId: string; tenantId: string; roleCode: string; roleName: string; description: string; roleType: string; status: string; systemManaged: boolean; createdAt: string; updatedAt: string; version: number; }
export interface Permission { permissionCode: string; displayName: string; description: string; allowedScopeTypes: string[]; systemManaged: boolean; }
export interface RolePermissionMatrix { roleId: string; permissions: Permission[]; }
export interface ResponsibilityTemplate { roleId: string; tenantId: string; roleCode: string; roleName: string; description: string; roleType: string; status: string; systemManaged: boolean; riskLevel: string; reviewRequired: boolean; nextReviewAt?: string | null; permissionCount: number; assignmentCount: number; activeAssignmentCount: number; expiringAssignmentCount: number; allowedScopeTypes: string[]; capabilityCodes: string[]; allowedPrincipalTypes: string[]; version: number; }
export interface AccessAssignment { bindingId: string; principalType: string; principalId: string; principalName: string; roleId: string; roleCode: string; roleName: string; riskLevel: string; scopeType: string; scopeId: string; scopeName: string; effectiveAt: string; expiresAt?: string | null; status: string; lifecycleStatus: string; reviewRequired: boolean; nextReviewAt?: string | null; version: number; }
export interface AccessLifecycleSummary { tenantId: string; activeAssignments: number; expiringAssignments: number; expiredAssignments: number; scheduledAssignments: number; pendingApprovals: number; criticalAssignments: number; reviewDueAssignments: number; orphanAssignments: number; }
export interface AccessReviewCandidate { bindingId: string; principalType: string; principalId: string; principalName: string; roleId: string; roleCode: string; roleName: string; riskLevel: string; scopeType: string; scopeId: string; scopeName: string; effectiveAt: string; expiresAt?: string | null; nextReviewAt?: string | null; lastReviewedAt?: string | null; reviewReason: string; version: number; }
export interface RoleBinding { bindingId: string; principalType: string; principalId: string; roleId: string; scopeType: string; scopeId: string; effectiveAt: string; expiresAt?: string; status: string; version: number; }
export interface EffectiveAccessSource { bindingId: string; roleId: string; roleName: string; principalType: string; principalId: string; scopeType: string; scopeId: string; effectiveAt: string; expiresAt?: string | null; inheritedFromGroup: boolean; }
export interface EffectivePermission { permissionCode: string; sources: EffectiveAccessSource[]; observations: string[]; }
export interface EffectiveAccess { tenantId: string; userId: string; evaluatedAt: string; permissions: EffectivePermission[]; conflicts: string[]; }
export interface UiAccessGrantReason { permissionCode: string; bindingId: string; roleId: string; roleName: string; principalType: string; principalId: string; scopeType: string; scopeId: string; effectiveAt: string; expiresAt?: string | null; inheritedFromGroup: boolean; }
export interface EffectiveUiAccess { tenantId: string; userId: string; evaluatedAt: string; uiAccess: UiEntitlementResponse; pageReasons: Record<string, UiAccessGrantReason[]>; actionReasons: Record<string, UiAccessGrantReason[]>; }
export interface ResponsibilityUiAccessPreview { tenantId: string; roleId: string; generatedAt: string; permissionCodes: string[]; allowedScopeTypes: string[]; uiAccess: UiEntitlementResponse; }
export interface UserAuthenticationReadiness { tenantId: string; userId: string; authenticationMethod: string; accountStatus: string; signInState: 'READY'|'SSO_MANAGED'|'SSO_LINK_REQUIRED'|'SETUP_REQUIRED'|'MFA_REQUIRED'|'LOCKED'|'SUSPENDED'|'DISABLED'|string; signInReady: boolean; passwordState: 'NOT_CONFIGURED'|'CHANGE_REQUIRED'|'CONFIGURED'|string; passwordChangedAt?: string | null; passwordExpiresAt?: string | null; mfaState: 'NOT_ENROLLED'|'PENDING'|'ACTIVE'|string; mfaType?: string | null; mfaVerifiedAt?: string | null; recoveryCodesRemaining: number; lastSuccessfulLogin?: string | null; failedLoginCount: number; lockedUntil?: string | null; activeSessionCount: number; setupState: string; setupExpiresAt?: string | null; deliveryMethod: string; deliveryStatus: string; deliveryReference: string; deliveryFailureCode: string; blockingActions: string[]; }
export interface MachineOwnershipImpact { serviceAccountCount: number; agentBusinessOwnerCount: number; agentTechnicalStewardCount: number; ownershipReviewRequiredCount: number; hasBlockingOwnership: boolean; }
export interface MachineOwnershipTransferResponse { transferId: string; tenantId: string; fromUserId: string; toUserId: string; serviceAccountsUpdated: number; agentBusinessOwnersUpdated: number; agentTechnicalStewardsUpdated: number; actorId: string; reason: string; transferredAt: string; }
export interface UserAccessOverview { tenantId: string; user: User; memberships: Membership[]; effectiveAccess: EffectiveAccess; authenticationReadiness: UserAuthenticationReadiness; sessions: Session[]; machineOwnershipImpact: MachineOwnershipImpact; }
export interface CredentialSetupResponse { userId: string; purpose: string; deliveryId: string; deliveryMethod: string; deliveryStatus: string; deliveryReference: string; expiresAt?: string | null; failureCode: string; setupActionUrl: string; }
export interface RoleBindingDraft { principalType: 'USER'|'DEPARTMENT'|'GROUP'|'SERVICE_ACCOUNT'|'AGENT'; principalId: string; roleId: string; scopeType: 'INSTANCE'|'TENANT'|'DEPARTMENT'|'DEPARTMENT_SUBTREE'|'GROUP'; scopeId: string; effectiveAt?: string | null; expiresAt?: string | null; approvalId?: string | null; }

export interface RbacHardeningPreview { operation:string; requestHash:string; critical:boolean; approvalRequired:boolean; selfEscalation:boolean; beforePermissions:string[]; afterPermissions:string[]; addedPermissions:string[]; removedPermissions:string[]; conflicts:string[]; warnings:string[]; }
export interface RbacCriticalApproval { approvalId:string; tenantId:string; operation:string; requestHash:string; requesterId:string; targetType:string; targetId:string; status:'PENDING'|'APPROVED'|'REJECTED'|'CONSUMED'|'EXPIRED'; approverId:string; decisionReason:string; requestedAt:string; expiresAt:string; decidedAt?:string|null; consumedAt?:string|null; version:number; }
export interface RoleBindingAssignmentPreview { tenantId: string; principalType: string; principalId: string; roleId: string; roleName: string; scopeType: string; scopeId: string; effectiveAt: string; expiresAt?: string | null; newlyEffectivePermissions: string[]; alreadyEffectivePermissions: string[]; warnings: string[]; }
export interface RoleBindingRevocationPreview { bindingId: string; roleId: string; roleName: string; scopeType: string; scopeId: string; permissionsLost: string[]; permissionsRetained: string[]; warnings: string[]; }
export interface ServiceAccount { tenantId: string; serviceAccountId: string; name: string; description: string; ownerUserId: string; ownerDepartmentId: string; responsibilityBindingId: string; responsibilityRoleId: string; permissions: string[]; audiences: string[]; apiPrefixes: string[]; cidrs: string[]; machineScopes: string[]; allowedSourceSystems: string[]; tokenMaxTtlSeconds: number; maxActiveTokens: number; credentialMaxTtlSeconds: number; maxActiveCredentials: number; rateLimitPerMinute: number; nextReviewAt: string; riskLevel: string; status: string; version: number; }
export interface ServiceAccountCredential { tenantId: string; credentialId: string; serviceAccountId: string; credentialType: string; name: string; clientId: string; last4: string; status: string; issuedAt: string; expiresAt: string; lastUsedAt?: string | null; rotationGraceExpiresAt?: string | null; useCount: number; version: number; }
export interface IssuedServiceAccountCredential { credentialId: string; serviceAccountId: string; clientId: string; clientSecret: string; last4: string; issuedAt: string; expiresAt: string; }
export interface TokenSummary { tokenId: string; tokenType: string; principalType: string; principalId: string; name: string; prefix: string; last4: string; status: string; issuedAt: string; expiresAt: string; lastUsedAt?: string; version: number; }
export interface IssuedToken { tokenId: string; token: string; tokenType: string; prefix: string; last4: string; expiresAt: string; permissions: string[]; audiences: string[]; apiPrefixes: string[]; cidrs: string[]; }
export interface Session { sessionId: string; subjectType: string; subjectId: string; tenantId: string; methods: string[]; createdAt: string; lastSeenAt: string; idleExpiresAt: string; absoluteExpiresAt: string; ipAddress: string; userAgent: string; status: string; version: number; }
export interface IdentityAudit { eventId: string; eventType: string; actorType: string; actorId: string; targetType: string; targetId: string; tenantId: string; permission: string; decisionId: string; correlationId: string; reason: string; occurredAt: string; }
export interface SecurityPolicies { tenantId: string; password: Record<string, unknown>; mfa: Record<string, unknown>; session: Record<string, unknown>; token: Record<string, unknown>; version: number; }

export interface PermissionCatalogRevision { revisionId: string; revisionCode: string; revisionNumber: number; status: 'DRAFT'|'PUBLISHED'|'SUPERSEDED'|'RETIRED'; contentHash: string; description: string; supersedesRevisionId?: string | null; createdAt: string; createdBy: string; publishedAt?: string | null; publishedBy?: string | null; version: number; }
export interface PermissionCatalogDefinition { revisionId: string; permissionCode: string; ownerModule: string; resourceType: string; actionCode: string; description: string; riskLevel: string; riskLane: 'READ'|'WRITE'|'EXPORT'|'ADMIN'|'CRITICAL'; lifecycle: 'DRAFT'|'ACTIVE'|'DEPRECATED'|'RETIRED'; allowedScopes: string[]; systemManaged: boolean; replacementPermissionCode?: string | null; introducedAt: string; deprecatedAt?: string | null; retiredAt?: string | null; updatedAt: string; updatedBy: string; version: number; }
export interface PermissionCatalogAlias { revisionId: string; aliasCode: string; canonicalPermissionCode: string; aliasType: string; validFrom: string; validUntil?: string | null; reason: string; createdAt: string; createdBy: string; version: number; }
export interface PermissionCatalogDiffItem { permissionCode: string; type: 'ADDED'|'CHANGED'|'REMOVED'; changedFields: string[]; before?: PermissionCatalogDefinition | null; after?: PermissionCatalogDefinition | null; }
export interface PermissionCatalogDiff { baseRevisionId: string; targetRevisionId: string; items: PermissionCatalogDiffItem[]; }
export interface PermissionCatalogValidationIssue { severity: 'ERROR'|'WARNING'; code: string; permissionCode: string; message: string; }
export interface PermissionCatalogValidation { revisionId: string; valid: boolean; entryCount: number; aliasCount: number; issues: PermissionCatalogValidationIssue[]; }
export interface PermissionCatalogPublicationResult { publicationId: string; revisionId: string; previousRevisionId?: string | null; contentHash: string; entryCount: number; aliasCount: number; actorId: string; publishedAt: string; }
export interface PermissionCatalogPublication { publicationId: string; revisionId: string; previousRevisionId?: string | null; contentHash: string; entryCount: number; aliasCount: number; actorId: string; auditReason: string; correlationId: string; publishedAt: string; }
export interface EntryPointAuthority { entryPointId:string; entryPointType:string; applicationId:string; ownerModule:string; displayName:string; routePattern?:string|null; httpMethod?:string|null; authorityState:'LEGACY_ONLY'|'DUAL_SHADOW'|'TARGET_READY'|'TARGET_ONLY'|'EXEMPT'; targetPermissionCode?:string|null; legacyAuthorityType?:string|null; legacyAuthorities:string[]; resourceType:string; resourceResolverId:string; exemptionReason?:string|null; migrationDeadline?:string|null; manifestRevision:string; sourceRef:string; sourceHash:string; lastVerifiedAt:string; version:number; unknownPermission:boolean; missingMapping:boolean; missingResolver:boolean; overdue:boolean; activeBypass:boolean; expiredBypass:boolean; }
export interface EntryPointBurnDownSummary { total:number; byState:Record<string,number>; unknownPermissions:number; missingMappings:number; missingResolvers:number; overdue:number; activeBypasses:number; expiredBypasses:number; cutoverBlockers:number; }
export interface LegacyAuthorityMapping { mappingId:string; legacyAuthorityType:string; legacyAuthorityCode:string; targetPermissionCode?:string|null; ownerModule:string; status:string; migrationDeadline?:string|null; notes:string; updatedAt:string; version:number; }
export interface EntryPointBypass { bypassId:string; entryPointId:string; ownerId:string; reason:string; replacement:string; expiresAt:string; status:string; createdAt:string; createdBy:string; revokedAt?:string|null; revokedBy?:string|null; revokeReason?:string|null; version:number; }
export interface ApplicationPermissionManifest { manifestId:string; applicationId:string; environment:string; buildVersion:string; manifestRevision:string; schemaVersion:number; manifestHash:string; catalogRevisionId:string; catalogRevisionCode:string; catalogContentHash:string; sourceInventoryRevision:string; entryCount:number; protectedEntryCount:number; coveredEntryCount:number; coveragePercent:number; targetPermissionCount:number; legacyAuthorityCount:number; exemptCount:number; delegatedCount:number; uncoveredCount:number; status:string; registeredAt:string; registeredBy:string; activatedAt?:string|null; activatedBy?:string|null; version:number; matchingEntries:number; sourceChanged:number; descriptorChanged:number; unregisteredSource:number; staleManifest:number; unknownPermission:number; retiredPermission:number; missingResolver:number; runtimeDriftBlockers:number; }
export interface ApplicationPermissionManifestEntry { manifestId:string; entryPointId:string; entryPointType:string; ownerModule:string; displayName:string; routePattern?:string|null; httpMethod?:string|null; authorityState:string; protectionMode:string; coverageStatus:string; permissionCode?:string|null; legacyAuthorities:string[]; resourceType:string; resourceResolverId:string; scopeRequired:boolean; exemptionReason?:string|null; sourceRef:string; sourceHash:string; descriptorHash:string; driftStatus:string; blocker:boolean; }
export interface PermissionManifestDriftSummary { manifestId:string; total:number; matching:number; sourceChanged:number; descriptorChanged:number; unregisteredSource:number; staleManifest:number; unknownPermission:number; retiredPermission:number; missingResolver:number; blockers:number; }
export interface PermissionCoverageEvidence { evidenceId:string; manifestId:string; evidenceType:string; manifestHash:string; catalogRevisionId:string; catalogContentHash:string; entryCount:number; coveredEntryCount:number; coveragePercent:number; sourceInventoryRevision:string; detailsJson:string; actorId:string; correlationId?:string|null; occurredAt:string; }
export interface ShadowObservation { tenantId:string;comparisonId:string;domainCode:string;entryPointId?:string|null;resourceType:string;resourceId:string;permissionCode:string;legacyEffect:string;legacyScope:string;legacyVisibility:string;legacyReasonCode:string;legacyContextComplete:boolean;targetEffect:string;targetScope:string;targetVisibility:string;targetReasonCode:string;targetContextComplete:boolean;category:string;severity:string;riskLane:string;protectionMode:string;correlationId:string;comparedAt:string;caseId?:string|null;caseStatus?:string|null;ownerId?:string|null;slaDueAt?:string|null;activeWaiver:boolean;cutoverBlocker:boolean; }
export interface ShadowObservationSummary { total:number;byCategory:Record<string,number>;critical:number;openCases:number;overdueCases:number;activeWaivers:number;cutoverBlockers:number; }
export interface ShadowMismatchCase { caseId:string;sourceTenantId:string;comparisonId:string;domainCode:string;entryPointId?:string|null;permissionCode:string;category:string;severity:string;status:string;ownerId:string;slaDueAt:string;title:string;resolution?:string|null;firstSeenAt:string;lastSeenAt:string;occurrenceCount:number;createdAt:string;createdBy:string;updatedAt:string;updatedBy:string;version:number;activeWaiver:boolean;regressionPassed:number;regressionFailed:number; }
export interface ShadowMismatchWaiver { waiverId:string;caseId:string;reason:string;approvedBy:string;approvedAt:string;expiresAt:string;status:string;revokedAt?:string|null;revokedBy?:string|null;version:number; }
export interface ShadowRegressionEvidence { evidenceId:string;caseId:string;testReference:string;result:string;detailsJson:string;executedAt:string;executedBy:string;createdAt:string; }
export interface DomainReadinessEvidence { evidenceId:string;sourceTenantId:string;domainCode:string;status:string;windowStartedAt:string;windowEndedAt:string;sampleCount:number;matchCount:number;unexpectedAllowCount:number;scopeWidenedCount:number;visibilityWidenedCount:number;targetErrorCount:number;contextIncompleteCount:number;unexpectedDenyCount:number;reasonDifferentCount:number;openBlockingCases:number;activeWaivers:number;failedRegressions:number;manifestCoveragePercent:number;runtimeDriftBlockers:number;entryPointBlockers:number;expiredBypasses:number;blockersJson:string;catalogRevisionId:string;manifestId?:string|null;actorId:string;auditReason:string;correlationId?:string|null;evaluatedAt:string; }
export interface Phase6EligibilityEvidence { evidenceId:string;sourceTenantId:string;status:string;requiredDomainsJson:string;domainEvidenceRefsJson:string;eligibleDomains:number;blockedDomains:number;observingDomains:number;catalogRevisionId:string;manifestId?:string|null;manifestCoveragePercent:number;runtimeDriftBlockers:number;entryPointBlockers:number;expiredBypasses:number;openBlockingCases:number;blockersJson:string;actorId:string;auditReason:string;correlationId?:string|null;evaluatedAt:string; }

export interface ShadowPipelineReadiness { pendingCount:number;processingCount:number;readyCount:number;oldestPendingSeconds:number;deadLetters24h:number;dropped24h:number;retries24h:number;processed24h:number;queueCapacity:number;enabled:boolean; }
export interface ShadowStorageReadiness { durableMismatchRows:number;sampledMatchRows:number;metricRows:number;receiptRows:number;archivedPartitions:number;overdueArchivePurges:number;durablePartitions:number;sampledPartitions:number;deadLetterPartitions:number;lastRetentionCompletedAt?:string|null; }
export interface ShadowRetentionRun { runId:string;status:string;metricsDeleted:number;receiptsDeleted:number;deadLettersDeleted:number;partitionsArchived:number;partitionsPurged:number;detailsJson:string;startedAt:string;completedAt?:string|null;actorId:string;auditReason:string;correlationId?:string|null; }
export interface Phase5RuntimeCertificationEvidence { evidenceId:string;status:string;sourceVersion:string;catalogRevisionId:string;manifestId?:string|null;postgresqlCleanStatus:string;postgresqlUpgradeStatus:string;applicationContextStatus:string;adminUiBuildStatus:string;playwrightStatus:string;loadTestStatus:string;pipelineStatus:string;evidenceJson:string;actorId:string;auditReason:string;correlationId?:string|null;generatedAt:string; }

// Phase 7: Security, credentials, audit and advanced governance.
export interface SecurityWorkspaceSummary {
  tenantId: string;
  activeSessions: number;
  sessionsExpiringSoon: number;
  activeServiceAccounts: number;
  serviceAccountsReviewDue: number;
  activeTokens: number;
  tokensExpiringSoon: number;
  dormantTokens: number;
  deniedDecisions24h: number;
  administrativeChanges24h: number;
}
export interface HumanReadableAudit {
  eventId: string;
  category: string;
  action: string;
  summary: string;
  actorId: string;
  actorName: string;
  targetType: string;
  targetId: string;
  targetName: string;
  outcome: string;
  reason: string;
  correlationId: string;
  occurredAt: string;
  technicalEvidence: string;
}
export interface CredentialAudience {
  audienceCode: string;
  displayName: string;
  description: string;
}
export interface CredentialApiProduct {
  productCode: string;
  displayName: string;
  description: string;
  audiences: string[];
  apiPrefixes: string[];
}
export interface CredentialMachineScope {
  scopeCode: string;
  displayName: string;
  description: string;
}
export interface CredentialSourceSystem {
  sourceSystemId: string;
  displayName: string;
  description: string;
  status: string;
}
export interface CredentialGovernanceCatalog {
  apiProducts: CredentialApiProduct[];
  audiences: CredentialAudience[];
  machineScopes: CredentialMachineScope[];
  sourceSystems: CredentialSourceSystem[];
}
export interface SecurityPolicyRevision {
  revisionId: string;
  tenantId: string;
  policyKind: 'password' | 'mfa' | 'session' | 'token' | string;
  policyVersion: number;
  policyJson: string;
  actorId: string;
  auditReason: string;
  correlationId: string;
  createdAt: string;
}

