package com.opensocket.aievent.core.enforcement.activation.application;

import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;

public record PublishCutoverResult(CutoverPlan plan, PublishedAuthorityRevision revision, SnapshotRefreshStatus runtime) {}
