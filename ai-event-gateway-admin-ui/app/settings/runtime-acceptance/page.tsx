import { redirect } from 'next/navigation';

export default function RuntimeAcceptancePage() {
  redirect('/settings/release-certification?view=runtime-proof');
}
