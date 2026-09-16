package com.opensocket.aievent.core.issuetracking.relay;
public record RelayPolicySignalResult(RelayPolicySignalStatus status,String evidenceReference,String safeMessage) {
 public static RelayPolicySignalResult published(String evidence){return new RelayPolicySignalResult(RelayPolicySignalStatus.PUBLISHED,evidence==null?"":evidence,"");}
 public static RelayPolicySignalResult unavailable(String message){return new RelayPolicySignalResult(RelayPolicySignalStatus.UNAVAILABLE,"",message==null?"Phase 2 policy signal port unavailable.":message);}
}
