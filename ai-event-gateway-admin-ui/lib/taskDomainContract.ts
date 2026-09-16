export const TASK_DETAIL_SECTIONS = [
  "Overview",
  "Ownership and Participants",
  "Agent Assignment",
  "A2A Chain",
  "Related Tasks",
  "External Issues",
  "Execution Evidence",
  "Integration Sync",
  "Audit Timeline",
] as const;

export const TASK_RELATIONSHIP_LABELS = {
  REFERENCES: "References", RELATED_TO: "Related to", DEPENDS_ON: "Depends on",
  BLOCKS: "Blocks", DUPLICATES: "Duplicates", CAUSED_BY: "Caused by",
  RESULT_OF: "Result of", SUPERSEDES: "Supersedes", RESOLVES: "Resolves",
  CONTRIBUTES_TO: "Contributes to", DERIVED_FROM: "Derived from", VALIDATES: "Validates",
} as const;

export const TASK_REFERENCE_COPY = {
  restricted: "Restricted reference",
  restrictedDescription: "A related Task exists, but its details are not visible in your current scope.",
  empty: "No related Tasks have been added.",
  add: "Add related Tasks",
  reverse: "Referenced by",
} as const;

export type TaskDetailSection = (typeof TASK_DETAIL_SECTIONS)[number];
