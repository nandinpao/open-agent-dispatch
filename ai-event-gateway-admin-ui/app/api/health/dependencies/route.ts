import { NextResponse } from 'next/server';
import { BackendConnectionError, backendOrigins, fetchBackend } from '@/lib/server/backendOrigins';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const { response, origin, attempts } = await fetchBackend('core', '/api/auth/csrf', {
      method: 'GET',
      headers: { Accept: 'application/json', 'x-admin-ui-health-probe': 'true' }
    });
    return NextResponse.json({
      status: response.ok ? 'UP' : 'DEGRADED',
      service: 'ai-event-gateway-admin-ui',
      dependencies: {
        coreAuthentication: {
          reachable: true,
          status: response.status,
          selectedOrigin: origin,
          fallbackAttempts: attempts
        }
      },
      timestamp: new Date().toISOString()
    }, { status: response.ok ? 200 : 503 });
  } catch (error) {
    const attempts = error instanceof BackendConnectionError ? error.attempts : [];
    return NextResponse.json({
      status: 'DOWN',
      service: 'ai-event-gateway-admin-ui',
      dependencies: {
        coreAuthentication: {
          reachable: false,
          configuredOrigins: backendOrigins('core'),
          attempts
        }
      },
      timestamp: new Date().toISOString()
    }, { status: 503 });
  }
}
