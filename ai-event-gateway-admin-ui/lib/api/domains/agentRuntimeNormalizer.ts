import type { CoreAgentProfile } from "@/lib/types/core";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isCoreAgentProfile(value: unknown): value is CoreAgentProfile {
  if (!isRecord(value)) return false;
  return (
    typeof value.agentId === "string" &&
    value.agentId.trim().length > 0 &&
    typeof value.approvalStatus === "string" &&
    typeof value.enabled === "boolean"
  );
}

function toList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (!isRecord(value)) return [];
  const candidates = [value.content, value.items, value.records, value.rows, value.data];
  return candidates.find(Array.isArray) ?? [];
}

/** Normalize the Core agent runtime-view wrapper without moving API or governance authority into the UI. */
export function normalizeCoreAgentRuntimeViewPayload(
  value: unknown,
): CoreAgentProfile[] {
  const list = toList(value);
  return list
    .map((item) => {
      if (!isRecord(item)) return undefined;
      const profile = item.profile;
      if (isCoreAgentProfile(profile)) return profile;
      if (isCoreAgentProfile(item)) return item;
      return undefined;
    })
    .filter((profile): profile is CoreAgentProfile => profile !== undefined);
}
