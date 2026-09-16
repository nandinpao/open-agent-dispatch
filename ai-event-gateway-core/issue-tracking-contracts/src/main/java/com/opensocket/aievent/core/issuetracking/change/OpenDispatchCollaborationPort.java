package com.opensocket.aievent.core.issuetracking.change;
public interface OpenDispatchCollaborationPort { OpenDispatchCollaborationResult applyProviderComment(ProviderCommentSyncCommand command); OpenDispatchCollaborationResult applyProviderRelation(ProviderRelationSyncCommand command); default String mode(){return "CUSTOM";} }
