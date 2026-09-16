package com.opensocket.aievent.core.resourceaccess.contract;
public interface ResourceExportAuthorizationPort { ResourceExportAuthorization authorize(ResourceExportCommand command); ExportArtifactCommitResult authorizeCommit(ExportArtifactCommitCommand command); }
