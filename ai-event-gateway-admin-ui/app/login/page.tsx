'use client';

import { FormEvent, useState } from 'react';
import { PageHeader } from '@/components/common/PageHeader';
import { useAuth } from '@/components/auth/AuthProvider';
import { getPublicEnv } from '@/lib/constants/env';

export default function LoginPage() {
  const env = getPublicEnv();
  const { login, status } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login({ username, password });
    } catch (err) {
      setError(err instanceof Error ? err.message : '登入失敗');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto max-w-md pt-12">
      <PageHeader title="Admin Login" description={env.adminAuthMode === 'core-session' ? '使用 Core Identity 與 HttpOnly Cookie Session 登入。' : 'Rollback 模式：透過伺服器端橋接使用舊 Netty 身分驗證。'} />
      <form onSubmit={onSubmit} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-4 rounded-xl border border-blue-100 bg-blue-50 px-3 py-2 text-xs font-semibold text-blue-800">Authentication mode: {env.adminAuthMode}</div>
        <label className="text-sm font-medium text-slate-700">Account</label>
        <input
          className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-100"
          placeholder="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoComplete="username"
          required
        />
        <label className="mt-4 block text-sm font-medium text-slate-700">Password</label>
        <input
          className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-100"
          placeholder="••••••••"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          required
        />
        {error ? (
          <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
            {error}
          </div>
        ) : null}
        <button
          className="mt-5 w-full rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
          disabled={submitting || status === 'CHECKING'}
          type="submit"
        >
          {submitting ? 'Signing in...' : 'Sign in'}
        </button>
        <div className="mt-4 space-y-1 text-xs text-slate-500">
          <div>Core：{env.coreApiBaseUrl}</div>
          <div>Netty：{env.nettyApiBaseUrl}</div>
          <div>Auth：{env.authEnabled ? 'enabled' : 'disabled'} / Mock：{env.useMock ? 'enabled' : 'disabled'}</div>
        </div>
      </form>
    </main>
  );
}
