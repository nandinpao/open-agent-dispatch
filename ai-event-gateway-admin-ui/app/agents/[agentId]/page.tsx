import { AgentDetailProductView } from '@/components/agents/AgentDetailProductView';

export default async function AgentDetailPage({ params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  return <AgentDetailProductView agentId={decodeURIComponent(agentId)} />;
}
