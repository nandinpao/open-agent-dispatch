import { NextRequest, NextResponse } from 'next/server';
import { proxyAdminAuth } from '@/lib/server/authProxy';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

async function proxy(request: NextRequest, context: { params: Promise<{ path?: string[] }> }) {
  const { path = [] } = await context.params;
  return proxyAdminAuth(request, path);
}

export function OPTIONS() {
  return new NextResponse(null, { status: 204 });
}

export const GET = proxy;
export const POST = proxy;
