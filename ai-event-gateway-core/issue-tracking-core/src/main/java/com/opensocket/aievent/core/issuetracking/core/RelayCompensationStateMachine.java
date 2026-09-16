package com.opensocket.aievent.core.issuetracking.core;
import com.opensocket.aievent.core.issuetracking.relay.RelayCompensationStatus;
public final class RelayCompensationStateMachine {
 public RelayCompensationStatus afterExecution(int total,int compensated,int failed,boolean retryableFailure){
  if(total<1)return RelayCompensationStatus.DECISION_REQUIRED;
  if(compensated==total)return RelayCompensationStatus.COMPLETED;
  if(compensated>0)return RelayCompensationStatus.PARTIAL;
  if(failed>0)return retryableFailure?RelayCompensationStatus.FAILED_RETRYABLE:RelayCompensationStatus.FAILED_PERMANENT;
  return RelayCompensationStatus.IN_PROGRESS;
 }
}
