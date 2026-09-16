package com.opensocket.aievent.core.issuetracking.change;
public record OpenDispatchCollaborationResult(boolean accepted,boolean retryable,String evidenceReference,String reasonCode,String safeMessage) {
 public static OpenDispatchCollaborationResult accepted(String evidence){return new OpenDispatchCollaborationResult(true,false,evidence,"ACCEPTED","OpenDispatch collaboration update accepted.");}
 public static OpenDispatchCollaborationResult unavailable(String code){return new OpenDispatchCollaborationResult(false,true,"",code,"OpenDispatch collaboration command port is unavailable.");}
}
