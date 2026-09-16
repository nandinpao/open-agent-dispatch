'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { resolveEntitlementRoute } from '@/lib/navigation/uiEntitlements';

export default function AuthorizedHomePage() {
  const router = useRouter();
  const entitlements = useUiEntitlements();

  useEffect(() => {
    if (!entitlements.value) return;
    const navigation = entitlements.value.navigation ?? [];
    const businessHome = navigation.find((item) => item.featureId !== 'my-account');
    const accountHome = navigation.find((item) => item.featureId === 'my-account');
    const selected = businessHome ?? accountHome;
    const target = selected
      ? resolveEntitlementRoute(selected.route, entitlements.value.tenantId)
      : '/account';
    router.replace(target);
  }, [entitlements.value, router]);

  if (entitlements.error) {
    return (
      <section className="mx-auto max-w-2xl rounded-3xl border border-amber-300 bg-amber-50 p-6 text-amber-950 shadow-sm">
        <h1 className="text-xl font-black">Workspace access could not be resolved</h1>
        <p className="mt-2 text-sm leading-6">{entitlements.error}</p>
        <button type="button" onClick={entitlements.refresh} className="mt-4 rounded-xl bg-amber-900 px-4 py-2 text-sm font-black text-white">
          Retry access check
        </button>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-2xl rounded-3xl border border-slate-200 bg-white p-6 shadow-sm" role="status">
      <h1 className="text-xl font-black text-slate-950">Opening your workspace</h1>
      <p className="mt-2 text-sm leading-6 text-slate-600">
        OpenDispatch is resolving the first Navigator destination authorized by your current responsibilities.
      </p>
    </section>
  );
}
