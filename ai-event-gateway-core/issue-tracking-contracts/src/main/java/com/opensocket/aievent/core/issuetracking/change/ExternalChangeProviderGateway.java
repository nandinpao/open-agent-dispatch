package com.opensocket.aievent.core.issuetracking.change;
public interface ExternalChangeProviderGateway { ProviderSyncResult appendComment(ProviderCommentSyncCommand command); ProviderSyncResult createRelation(ProviderRelationSyncCommand command); default String mode(){return "CUSTOM";} }
