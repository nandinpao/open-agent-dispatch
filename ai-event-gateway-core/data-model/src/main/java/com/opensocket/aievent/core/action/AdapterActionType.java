package com.opensocket.aievent.core.action;

public enum AdapterActionType {
    MCP_CONTEXT_FETCH,
    ISSUE_READ,
    ISSUE_CREATE,
    ISSUE_COMMENT,
    ISSUE_UPDATE,
    /** @deprecated compatibility alias for ISSUE_COMMENT. */
    @Deprecated
    ISSUE_UPDATE_COMMENT
}
