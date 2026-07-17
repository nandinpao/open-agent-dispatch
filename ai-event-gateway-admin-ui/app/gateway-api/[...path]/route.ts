import { NextRequest, NextResponse } from 'next/server';
import { proxyToBackend } from '@/lib/server/backendProxy';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function proxy(request: NextRequest, context: { params: Promise<{ path?: string[] }> }) {
  return proxyToBackend(request, context, 'gateway');
}

export function OPTIONS() {
  return new NextResponse(null, { status: 204 });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
