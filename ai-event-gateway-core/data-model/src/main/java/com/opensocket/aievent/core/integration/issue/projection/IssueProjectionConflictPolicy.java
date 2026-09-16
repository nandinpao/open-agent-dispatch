package com.opensocket.aievent.core.integration.issue.projection;
/** External providers never win Task lifecycle authority. */
public enum IssueProjectionConflictPolicy { CORE_WINS, EXTERNAL_METADATA_ONLY, MANUAL_REVIEW, SUSPEND_ON_CONFLICT }
