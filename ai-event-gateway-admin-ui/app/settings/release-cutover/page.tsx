import { redirect } from 'next/navigation';

export default function ReleaseCutoverPage() {
  redirect('/settings/release-certification?view=cutover');
}
