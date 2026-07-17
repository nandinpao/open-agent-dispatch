export type OperatorRepairGuide = {
  code: string;
  title: string;
  owner: string;
  description: string;
  href: string;
  actionLabel: string;
};

export const p3kOperatorRepairGuides: OperatorRepairGuide[] = [
  {
    code: 'P3H_NO_ACTIVE_POLICY_SCOPE',
    title: 'Create or activate Dispatch Policy v2 scope',
    owner: 'Dispatch Policies',
    description: 'The task cannot match Demand to Supply because no active Policy v2 scope covers the source system and task type.',
    href: '/dispatch-flows',
    actionLabel: 'Fix Policy Scope',
  },
  {
    code: 'P3H_ACTIVE_RUNTIME_BINDING_REQUIRED',
    title: 'Activate Agent Runtime Binding',
    owner: 'Runtime Resources',
    description: 'Runtime observation alone is not dispatch authority. Bind the Agent to a trusted Runtime Resource and activate the binding.',
    href: '/settings/runtime-resources',
    actionLabel: 'Fix Runtime Binding',
  },
  {
    code: 'P3H_RUNTIME_BINDING_NOT_ACTIVE',
    title: 'Resume or replace inactive runtime binding',
    owner: 'Runtime Resources',
    description: 'The candidate has a runtime binding, but it is not active. Resume it or bind a different trusted runtime.',
    href: '/settings/runtime-resources',
    actionLabel: 'Review Runtime Binding',
  },
  {
    code: 'P3H_REQUIRED_CAPABILITY_MISSING',
    title: 'Approve reusable capability for the supply profile',
    owner: 'Capability Catalog / Supply Profiles',
    description: 'The Policy requires a reusable supply capability that is absent from the Supply Profile capability snapshot.',
    href: '/settings/capabilities',
    actionLabel: 'Fix Capability',
  },
  {
    code: 'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
    title: 'Trust required runtime feature',
    owner: 'Runtime Feature Catalog',
    description: 'The runtime did not provide a trusted feature required by the Policy, such as TASK_ACK or TASK_RESULT.',
    href: '/settings/runtime-features',
    actionLabel: 'Fix Runtime Feature Trust',
  },
  {
    code: 'P3H_QUALITY_RULE_FAILED',
    title: 'Repair quality metrics or lower policy threshold',
    owner: 'Quality Metrics / Dispatch Policies',
    description: 'The candidate failed a quality rule such as successRate, recentFailureCount, or slaBreachRate.',
    href: '/settings/quality-metrics',
    actionLabel: 'Fix Quality Metrics',
  },
  {
    code: 'P3H_QUALITY_METRIC_UNAVAILABLE',
    title: 'Create quality snapshot',
    owner: 'Quality Metrics',
    description: 'The Policy references a quality metric that is not available for this Supply Profile.',
    href: '/settings/quality-metrics',
    actionLabel: 'Create Quality Snapshot',
  },
  {
    code: 'P3H_AGENT_PROFILE_MISSING',
    title: 'Complete Agent governance profile',
    owner: 'Agents',
    description: 'The candidate does not have the required Agent governance state to participate in dispatch.',
    href: '/agents',
    actionLabel: 'Fix Agent',
  },
];

export function repairGuideForBlockingCode(code?: string): OperatorRepairGuide | undefined {
  if (!code) return undefined;
  return p3kOperatorRepairGuides.find((guide) => guide.code === code);
}
