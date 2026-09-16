import { NextRequest } from 'next/server';
import { parseOperationalTypes, searchScopedOperationalData, type OperationalHit } from '@/lib/server/scopedOperationalData';
export const runtime = 'nodejs'; export const dynamic = 'force-dynamic';
const ALLOWED_LIMITS = new Set([100, 250, 500]);
function csv(value: unknown): string { const text = value == null ? '' : String(value); return `"${text.replace(/"/g, '""')}"`; }
function safeRow(item: OperationalHit) { return { type:item.type,id:item.id,title:item.title,subtitle:item.subtitle??'',status:item.status??'',ownerDepartmentId:item.ownerDepartmentId??'',ownerGroupId:item.ownerGroupId??'',updatedAt:item.updatedAt??'',href:item.href }; }
export async function GET(request: NextRequest): Promise<Response> {
  const type = parseOperationalTypes([request.nextUrl.searchParams.get('type') ?? ''])[0];
  const format = (request.nextUrl.searchParams.get('format') ?? 'CSV').toUpperCase();
  const requestedLimit = Number(request.nextUrl.searchParams.get('limit') ?? '100');
  const query = request.nextUrl.searchParams.get('q') ?? '';
  if (!type || !['CSV','JSON'].includes(format) || !ALLOWED_LIMITS.has(requestedLimit)) return Response.json({ code:'INVALID_EXPORT_SELECTION', message:'Choose a supported resource type, format, and row limit.' }, { status:400 });
  const cookie = request.headers.get('cookie') ?? '';
  const rows = (await searchScopedOperationalData(cookie, query, [type], requestedLimit)).map(safeRow);
  const stamp = new Date().toISOString().slice(0,10);
  if (format === 'JSON') return new Response(JSON.stringify(rows, null, 2), { headers:{'Content-Type':'application/json; charset=utf-8','Content-Disposition':`attachment; filename="opendispatch-${type.toLowerCase()}-scoped-${stamp}.json"`,'Cache-Control':'no-store'} });
  const columns = ['type','id','title','subtitle','status','ownerDepartmentId','ownerGroupId','updatedAt','href'] as const;
  const body = [columns.join(','), ...rows.map((row) => columns.map((column) => csv(row[column])).join(','))].join('\n');
  return new Response(body, { headers:{'Content-Type':'text/csv; charset=utf-8','Content-Disposition':`attachment; filename="opendispatch-${type.toLowerCase()}-scoped-${stamp}.csv"`,'Cache-Control':'no-store'} });
}
