import { NextRequest } from 'next/server';
import { loadScopedOperationalData } from '@/lib/server/scopedOperationalData';
export const runtime = 'nodejs'; export const dynamic = 'force-dynamic';
export async function GET(request: NextRequest): Promise<Response> {
  const cookie = request.headers.get('cookie') ?? '';
  try { return Response.json(await loadScopedOperationalData(cookie), { headers: { 'Cache-Control': 'no-store' } }); }
  catch { return Response.json({ code: 'SCOPED_DASHBOARD_UNAVAILABLE', message: 'Your accessible operations could not be loaded.' }, { status: 503 }); }
}
