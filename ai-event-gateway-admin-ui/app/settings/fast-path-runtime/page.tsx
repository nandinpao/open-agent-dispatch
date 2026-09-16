import { PageHeader } from '@/components/common/PageHeader';
import { FastPathRuntimeConsole } from '@/components/capabilities/FastPathRuntimeConsole';

export default function FastPathRuntimePage() {
  return <main className="space-y-5"><PageHeader title="Fast Path Runtime" description="Certify learned semantic patterns for runtime use, compare SHADOW plans without affecting real execution, and operate Tenant kill switches. Runtime Fast Path may skip Triage/Planner only; every Step still requires current WHO MAY → WHO SHOULD → HOW."/><FastPathRuntimeConsole/></main>;
}
