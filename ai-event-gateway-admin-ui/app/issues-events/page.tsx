import { PageHeader } from '@/components/common/PageHeader';
import { HubQuickLinks } from '@/components/common/HubQuickLinks';

export default function IssuesEventsPage() {
  return (
    <main className="space-y-6">
      <PageHeader
        title="Issues & Events"
        description="Review issue tracking integrations, failed work, security events, and runtime event logs from one operator entry point."
      />
      <HubQuickLinks
        title="Issue and event workspaces"
        description="Use these links when a task failed, an agent was rejected, or an external issue tracker needs verification."
        links={[
          { href: '/settings/issue-tracking', label: 'Issue Tracking', description: 'Configure and test Redmine or GitLab issue synchronization.' },
          { href: '/tasks/failure-queue', label: 'Failure Queue', description: 'Review failed tasks and recovery candidates.' },
          { href: '/security-events', label: 'Security Events', description: 'Review authorization, trust, and runtime security events.' },
          { href: '/events', label: 'Event Log', description: 'Inspect event history and runtime observations.' },
          { href: '/runtime/rejected-connections', label: 'Rejected Connections', description: 'Investigate rejected agent connection attempts.' },
          { href: '/websocket', label: 'Runtime Stream', description: 'Inspect live runtime event streams for diagnostics.' },
        ]}
      />
    </main>
  );
}
