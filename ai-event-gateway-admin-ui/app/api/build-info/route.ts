import { NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

const SAFE_HEADERS = {
  'Cache-Control': 'no-store, max-age=0',
  Pragma: 'no-cache',
};

export async function GET() {
  return NextResponse.json({
    releaseContract: 'PHASE_7_BUILD_FIX19_RUNTIME_ARTIFACT',
    maxIamPageSize: 100,
    enforcementActivationControlPlaneRequiredForLocalIam: true,
    tenantRouteCompatibility: 'ONBOARDING_AND_INVITATION_V1',
    iamLifecycleRouteRegistration: 'STRICT_NO_TRAILING_SLASH_V1',
    coreDashboardTenantRoutes: 'EXPLICIT_TENANT_PATH_V1',
    invitedOrganizationPreprovisioning: 'INVITED_AND_ACTIVE_V1',
    invitedUserAdministrativeVisibility: 'NON_REMOVED_MEMBERSHIP_V1',
    rootInstanceRouteProjection: 'REQUEST_SCOPED_INSTANCE_V1',
    wave0PermissionContract: 'CANONICAL_INSTANCE_READ_V1',
    tenantRbacApiTransactionBoundary: 'SERVICE_SCOPED_REQUIRED_V1',
  }, { status: 200, headers: SAFE_HEADERS });
}
