import { redirect } from 'next/navigation';

export default function ProductionFoundationPage() {
  redirect('/settings/release-certification?view=overview');
}
