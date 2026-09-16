package com.opensocket.aievent.core.issuetracking.application.change;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangeDecision;
public record ExternalChangeIngestionResult(ExternalChangeDecision decision,String commentSyncId,String relationSyncId,String candidateId,String reasonCode) {}
