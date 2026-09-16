import { NextResponse } from 'next/server';
import { BackendConnectionError } from '@/lib/server/backendOrigins';
import { loadRuntimeCapabilities, RuntimeCapabilityError } from '@/lib/server/runtimeCapabilities';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const revalidate = 0;

const SAFE_HEADERS: HeadersInit = {
  'Cache-Control': 'no-store, max-age=0',
  Pragma: 'no-cache',
  'X-Content-Type-Options': 'nosniff',
};

export async function GET(): Promise<NextResponse> {
  try {
    return NextResponse.json(await loadRuntimeCapabilities(), { status: 200, headers: SAFE_HEADERS });
  } catch (error) {
    if (error instanceof RuntimeCapabilityError) {
      return NextResponse.json(
        { code: error.code, message: error.message },
        { status: error.status, headers: SAFE_HEADERS },
      );
    }
    if (error instanceof BackendConnectionError) {
      console.error('[runtime-capability-authority]', error.attempts);
    } else {
      console.error('[runtime-capability-authority]', error);
    }
    return NextResponse.json(
      { code: 'RUNTIME_CAPABILITY_UNAVAILABLE', message: 'Core runtime capability authority is unavailable.' },
      { status: 503, headers: SAFE_HEADERS },
    );
  }
}
