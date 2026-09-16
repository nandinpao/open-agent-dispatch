package com.opensocket.aievent.core.issuetracking.core;
import com.opensocket.aievent.core.issuetracking.recovery.*;
public final class ProjectionCoalescingPolicy { public boolean coalescible(ProjectionOperationType operation){return operation==ProjectionOperationType.UPDATE;} public boolean preserveOrder(ProjectionOperationType operation){return operation==ProjectionOperationType.COMMENT||operation==ProjectionOperationType.RELATION||operation==ProjectionOperationType.CREATE||operation==ProjectionOperationType.CLOSE;} }
