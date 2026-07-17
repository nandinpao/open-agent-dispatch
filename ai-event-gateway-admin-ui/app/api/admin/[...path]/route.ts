import { NextRequest, NextResponse } from 'next/server';
import { proxyToBackend } from '@/lib/server/backendProxy';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Legacy Admin API compatibility route.
 *
 * `/api/admin/*` remains the Netty runtime-plane compatibility namespace.
 * Human authentication now uses `/api/auth/*`, which is proxied to Core by default.
 * This route must never handle browser login or expose browser bearer tokens.
 */
function proxy(request: NextRequest, context: { params: Promise<{ path?: string[] }> }) {
  return proxyToBackend(request, context, 'netty');
}

export function OPTIONS() {
  return new NextResponse(null, { status: 204 });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
