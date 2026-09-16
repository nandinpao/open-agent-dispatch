package com.opensocket.aievent.core.issuetracking.core;
import com.opensocket.aievent.core.issuetracking.change.ProviderActionCandidateStatus;
/** Pure transition guard for controlled provider action candidates. */
public final class ProviderActionCandidateStateMachine {
 public void requireTransition(ProviderActionCandidateStatus from,ProviderActionCandidateStatus to){if(!allowed(from,to))throw new IllegalStateException("CANDIDATE_TRANSITION_NOT_ALLOWED:"+from+"->"+to);}
 public boolean allowed(ProviderActionCandidateStatus from,ProviderActionCandidateStatus to){return switch(from){
  case PENDING_REVIEW -> to==ProviderActionCandidateStatus.WAIT_REAUTHENTICATION||to==ProviderActionCandidateStatus.WAIT_APPROVAL||to==ProviderActionCandidateStatus.APPROVED||to==ProviderActionCandidateStatus.REJECTED||to==ProviderActionCandidateStatus.SUPERSEDED;
  case WAIT_REAUTHENTICATION -> to==ProviderActionCandidateStatus.WAIT_APPROVAL||to==ProviderActionCandidateStatus.APPROVED||to==ProviderActionCandidateStatus.REJECTED;
  case WAIT_APPROVAL -> to==ProviderActionCandidateStatus.APPROVED||to==ProviderActionCandidateStatus.REJECTED;
  case APPROVED -> to==ProviderActionCandidateStatus.EXECUTION_PENDING||to==ProviderActionCandidateStatus.SUPERSEDED;
  case EXECUTION_PENDING -> to==ProviderActionCandidateStatus.EXECUTING||to==ProviderActionCandidateStatus.EXECUTION_FAILED;
  case EXECUTING -> to==ProviderActionCandidateStatus.EXECUTED||to==ProviderActionCandidateStatus.EXECUTION_FAILED;
  case EXECUTION_FAILED -> to==ProviderActionCandidateStatus.EXECUTION_PENDING||to==ProviderActionCandidateStatus.REJECTED;
  case REJECTED,EXECUTED,SUPERSEDED -> false;};}
}
