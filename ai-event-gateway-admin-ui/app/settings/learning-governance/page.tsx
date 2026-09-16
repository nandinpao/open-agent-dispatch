import { PageHeader } from '@/components/common/PageHeader';
import { LearningGovernanceWorkspace } from '@/components/learning-governance/LearningGovernanceWorkspace';

export default function LearningGovernancePage() {
  return (
    
      <main className="space-y-5">
        <PageHeader title="Learning Governance" description="Review execution-memory recommendations, semantic Fast Path candidates, human certifications and non-authoritative runtime hints." />
        <LearningGovernanceWorkspace />
      </main>
    
  );
}
