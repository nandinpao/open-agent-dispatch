import { AdminPageHeader } from '@/components/layout/AdminPageHeader';

export function PageHeader({ title, description }: Readonly<{ title: string; description: string }>) {
  return <AdminPageHeader title={title} description={description} className="mb-6" />;
}
