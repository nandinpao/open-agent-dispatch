import { NextRequest, NextResponse } from 'next/server';
import { BackendConnectionError, backendOrigins, fetchBackend } from '@/lib/server/backendOrigins';

const HOP_BY_HOP_HEADERS = new Set(['connection','keep-alive','proxy-authenticate','proxy-authorization','te','trailer','transfer-encoding','upgrade','host','content-length','content-encoding']);

function forwardHeaders(request: NextRequest): Headers {
  const headers = new Headers();
  request.headers.forEach((value,key) => { const n=key.toLowerCase(); if (!HOP_BY_HOP_HEADERS.has(n) && n !== 'origin') headers.set(key,value); });
  headers.set('x-forwarded-host', request.headers.get('host') ?? '');
  headers.set('x-forwarded-proto', request.nextUrl.protocol.replace(':',''));
  headers.set('x-forwarded-prefix','/api/auth');
  headers.set('x-admin-ui-proxy-plane', 'core-auth');
  return headers;
}

function responseHeaders(source: Headers): Headers {
  const result = new Headers();
  source.forEach((value,key) => { const n=key.toLowerCase(); if (!HOP_BY_HOP_HEADERS.has(n) && n !== 'set-cookie') result.append(key,value); });
  const extended=source as Headers & { getSetCookie?:()=>string[] };
  (extended.getSetCookie?.() ?? (source.get('set-cookie') ? [source.get('set-cookie') as string] : [])).forEach(v=>result.append('set-cookie',v));
  return result;
}

function proxyFailure(error: unknown): NextResponse {
  const attempts = error instanceof BackendConnectionError ? error.attempts : [];
  const detail = error instanceof Error ? error.message : String(error);
  console.error('[admin-auth-proxy] Core authentication request failed:', detail, attempts);
  return NextResponse.json({
    error: 'CORE_AUTH_UNAVAILABLE',
    message: `Admin UI cannot reach Core authentication. Tried: ${backendOrigins('core').join(', ')}`,
    details: {
      configuredOrigins: backendOrigins('core'),
      attempts,
      nextAction: 'Check the Admin UI container CORE_BACKEND_ORIGIN, Docker network membership, and the Core /api/auth/csrf endpoint.'
    }
  }, { status: 503 });
}

export async function proxyAdminAuth(request: NextRequest, path: string[]): Promise<NextResponse> {
  const pathname = `/api/auth/${path.map(encodeURIComponent).join('/')}`;
  const query = request.nextUrl.searchParams.toString();
  const pathAndQuery = query ? `${pathname}?${query}` : pathname;
  const method=request.method.toUpperCase();
  const hasBody=!['GET','HEAD'].includes(method);
  const body = hasBody ? new Uint8Array(await request.arrayBuffer()) : undefined;
  try {
    const { response: backend, origin, attempts } = await fetchBackend('core', pathAndQuery, {
      method,
      headers: forwardHeaders(request),
      body
    });
    if (attempts.length > 0) {
      console.warn('[admin-auth-proxy] Core fallback origin selected:', origin, attempts);
    }
    return new NextResponse(backend.body,{status:backend.status,statusText:backend.statusText,headers:responseHeaders(backend.headers)});
  } catch (error) {
    return proxyFailure(error);
  }
}
