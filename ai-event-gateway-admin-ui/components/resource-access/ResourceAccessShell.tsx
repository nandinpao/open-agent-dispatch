'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { useRuntimeCapabilities } from '@/components/providers/RuntimeCapabilityProvider';
import { runtimeSurfaceAvailable } from '@/lib/runtime-capability/contracts';

const links=[
 ['/resource-access','Overview'],
 ['/resource-access/grants','Scope Grants'],
 ['/resource-access/denies','Explicit Denies'],
 ['/resource-access/access-requests','Access Requests'],
 ['/resource-access/reviews','Access Reviews'],
 ['/resource-access/orphans','Orphan Resources'],
 ['/resource-access/decision-simulator','Decision Simulator'],
] as const;

export function ResourceAccessShell({title,description,children}:Readonly<{title:string;description:string;children:ReactNode}>){
 const pathname=usePathname();
 const {hasPermission,user}=useAuth();
 const {capabilities}=useRuntimeCapabilities();
 const state=capabilities.surfaces.resourceAccessAdministration;
 const enabled=runtimeSurfaceAvailable(state);
 if(!enabled)return <main className="mx-auto max-w-3xl rounded-3xl border border-slate-300 bg-slate-50 p-8 shadow-sm" role="alert"><p className="text-xs font-black uppercase tracking-[.18em] text-slate-500">Feature disabled</p><h1 className="mt-2 text-2xl font-black text-slate-950">Resource Access administration is not enabled</h1><p className="mt-3 text-sm leading-6 text-slate-600">Core reports Resource Access administration as disabled. Enable the corresponding backend governance APIs through the controlled rollout profile; the Admin UI no longer activates this workspace independently.</p></main>;
 if(!hasPermission('resource.governance.read'))return <main className="mx-auto max-w-3xl rounded-3xl border border-amber-200 bg-amber-50 p-8 text-amber-950 shadow-sm" role="alert"><p className="text-xs font-black uppercase tracking-[.18em]">Permission required</p><h1 className="mt-2 text-2xl font-black">Resource Access governance is unavailable</h1><p className="mt-3 text-sm leading-6">The authenticated identity <b>{user?.displayName??user?.username}</b> cannot open this governance workspace. Request the appropriate temporary access or contact an eligible administrator. Direct URL navigation does not bypass backend authorization.</p></main>;
 return <main className="space-y-6"><header><p className="text-xs font-black uppercase tracking-[.18em] text-indigo-600">Identity & Access · Resource Access</p><h1 className="mt-1 text-3xl font-black text-slate-950">{title}</h1><p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">{description}</p></header><nav className="flex gap-2 overflow-x-auto rounded-2xl border border-slate-200 bg-white p-2 shadow-sm" aria-label="Resource Access sections">{links.map(([href,label])=>{const active=pathname===href;return <Link key={href} href={href} className={`whitespace-nowrap rounded-xl px-4 py-2 text-sm font-black ${active?'bg-indigo-700 text-white':'text-slate-600 hover:bg-slate-100'}`}>{label}</Link>})}</nav>{children}</main>;
}
