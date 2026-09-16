package com.opensocket.aievent.core.issuetracking.core;
import com.opensocket.aievent.core.issuetracking.recovery.*;
public final class ProjectionOrderingPolicy {
 public ProjectionWorkStatus initialStatus(ProjectionOperationType operation,String dependsOnWorkId,boolean dependencyAcknowledged){ if((operation==ProjectionOperationType.COMMENT||operation==ProjectionOperationType.RELATION||operation==ProjectionOperationType.CLOSE)&&dependsOnWorkId!=null&&!dependsOnWorkId.isBlank()&&!dependencyAcknowledged) return ProjectionWorkStatus.BLOCKED; return ProjectionWorkStatus.READY; }
 public boolean mayExecute(OrderedProjectionWork work,long expectedSequence,boolean dependencyAcknowledged){ if(work==null||work.terminal())return false; if(work.laneSequence()!=expectedSequence)return false; if(work.dependsOnWorkId()!=null&&!work.dependsOnWorkId().isBlank()&&!dependencyAcknowledged)return false; return work.status()==ProjectionWorkStatus.READY||work.status()==ProjectionWorkStatus.RETRY_WAITING||work.status()==ProjectionWorkStatus.RECOVERY_WAITING_INDEX; }
 public boolean requiresExternalIssue(ProjectionOperationType operation){return operation==ProjectionOperationType.UPDATE||operation==ProjectionOperationType.CLOSE||operation==ProjectionOperationType.COMMENT||operation==ProjectionOperationType.RELATION;}
}
