package com.opensocket.aievent.core.integration.handoff;
import java.util.List; import java.util.Map;
public record AgentTaskContext(String taskId,String assignmentId,String dispatchRequestId,String agentId,String agentSessionId,String snapshotId,int snapshotVersion,String summary,Map<String,Object> structuredContext,List<String> allowedCommentRefs,List<HandoffAttachmentMetadata> attachmentMetadata,List<String> relatedTaskSummaries,String contentHash) {}
