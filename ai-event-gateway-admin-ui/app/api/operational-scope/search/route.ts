import { NextRequest } from 'next/server';
import { parseOperationalTypes, searchScopedOperationalData } from '@/lib/server/scopedOperationalData';
export const runtime = 'nodejs'; export const dynamic = 'force-dynamic';
export async function GET(request: NextRequest): Promise<Response> {
  const query = request.nextUrl.searchParams.get('q') ?? '';
  const types = parseOperationalTypes(request.nextUrl.searchParams.getAll('type'));
  const limit = Number(request.nextUrl.searchParams.get('limit') ?? '50');
  const cookie = request.headers.get('cookie') ?? '';
  try { return Response.json({ items: await searchScopedOperationalData(cookie, query, types, Number.isFinite(limit) ? limit : 50) }, { headers: { 'Cache-Control': 'no-store' } }); }
  catch { return Response.json({ code: 'SCOPED_SEARCH_UNAVAILABLE', message: 'Search is temporarily unavailable.' }, { status: 503 }); }
}
