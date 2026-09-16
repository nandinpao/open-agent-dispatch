package com.opensocket.aievent.core.task.domain;

public enum TaskRelationshipType {
    REFERENCES,
    RELATED_TO,
    DEPENDS_ON,
    BLOCKS,
    DUPLICATES,
    CAUSED_BY,
    RESULT_OF,
    SUPERSEDES,
    RESOLVES,
    CONTRIBUTES_TO,
    DERIVED_FROM,
    VALIDATES
}
