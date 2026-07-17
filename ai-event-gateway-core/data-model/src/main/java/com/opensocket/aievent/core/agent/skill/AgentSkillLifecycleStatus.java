package com.opensocket.aievent.core.agent.skill;

/** Lifecycle status for versioned Skill Registry definitions. */
public enum AgentSkillLifecycleStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    PUBLISHED,
    DEPRECATED,
    ROLLED_BACK
}
