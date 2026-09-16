package com.opensocket.aievent.core.issuetracking.application.change;
import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;import com.opensocket.aievent.core.issuetracking.change.*;
class Phase3GFailClosedPortTest {
 @Test void unavailableTaskCommandPortNeverAcceptsCandidate(){var result=new UnavailableProviderActionCommandPort().execute(new ProviderActionCommand("tenant","candidate",ProviderActionCandidateType.CLOSE_TASK,"{}",1,"idem","operator","reason","reauth","corr"));assertFalse(result.accepted());assertTrue(result.retryable());assertEquals("COMMAND_PORT_UNAVAILABLE",result.reasonCode());}
 @Test void unavailableCollaborationPortIsRetryableAndDoesNotClaimMutation(){var result=new UnavailableOpenDispatchCollaborationPort().applyProviderComment(new ProviderCommentSyncCommand("tenant","connection","mapping","project","ISSUE-1","comment","body","marker","idem","corr"));assertFalse(result.accepted());assertTrue(result.retryable());}
}
