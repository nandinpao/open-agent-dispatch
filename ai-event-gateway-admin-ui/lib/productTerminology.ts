/**
 * Phase 0A canonical English product terminology.
 *
 * Product components should import these labels instead of creating new aliases.
 * Full UI migration is completed in the English-only UI productization phase.
 */
export const PRODUCT_TERMS = Object.freeze({
  sourceSystem: "Source System",
  sourceFlow: "Source Flow",
  classificationRule: "Classification Rule",
  agentPool: "Agent Pool",
  poolMember: "Pool Member",
  agent: "Agent",
  runtimeEligibility: "Runtime Eligibility",
  task: "Task",
  rootTask: "Root Task",
  childTask: "Child Task",
  relatedTask: "Related Task",
  taskRelationship: "Task Relationship",
  taskParticipant: "Task Participant",
  assignedAgent: "Assigned Agent",
  externalAssignee: "External Assignee",
  a2aRequest: "A2A Request",
  a2aPolicy: "A2A Policy",
  integrationConnection: "Integration Connection",
  integrationPrincipal: "Integration Principal",
  integrationCredential: "Integration Credential",
  projectMapping: "Project Mapping",
  externalIssue: "External Issue",
  issueProjection: "Issue Projection",
  issueRelay: "Issue Relay",
  handoffContextSnapshot: "Handoff Context Snapshot",
  permissionProbe: "Permission Probe",
  auditTimeline: "Audit Timeline",
} as const);

export type ProductTermKey = keyof typeof PRODUCT_TERMS;

export const PRODUCT_STATUS_COPY = Object.freeze({
  mappingMissing: "No Project Mapping was found.",
  issueProjectionPending: "The External Issue has not been created yet. OpenDispatch will retry automatically.",
  relationDegraded: "The provider does not allow the requested relation. OpenDispatch will use a supported fallback.",
  noEligibleAgent: "No eligible Agent is currently available.",
  overPrivilegedPrincipal: "This Integration Principal has more permission than the Project Mapping requires.",
} as const);
