package com.opensocket.aievent.database.persistence.handoff.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.handoff.po.AgentContextAccessEventPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextApprovalPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextFieldDecisionPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextPolicyPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffContextSnapshotPo;
import com.opensocket.aievent.database.persistence.handoff.po.HandoffReleaseEvidencePo;
import com.opensocket.aievent.database.persistence.handoff.po.ResultContextSnapshotPo;

@Mapper
public interface HandoffContextDao {
    int upsertPolicy(@Param("value") HandoffContextPolicyPo value);
    HandoffContextPolicyPo findPolicy(@Param("tenantId") String tenantId, @Param("policyId") String policyId);
    List<HandoffContextPolicyPo> listPolicies(@Param("tenantId") String tenantId, @Param("limit") int limit);

    int insertSnapshot(@Param("value") HandoffContextSnapshotPo value);
    int updateSnapshotState(@Param("value") HandoffContextSnapshotPo value);
    HandoffContextSnapshotPo findSnapshot(@Param("tenantId") String tenantId, @Param("snapshotId") String snapshotId);
    HandoffContextSnapshotPo latestApprovedSnapshotForTask(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);
    List<HandoffContextSnapshotPo> listSnapshotsForTask(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId, @Param("limit") int limit);
    List<HandoffContextSnapshotPo> findReleaseCandidates(
            @Param("now") OffsetDateTime now, @Param("limit") int limit);

    int insertFieldDecision(@Param("value") HandoffContextFieldDecisionPo value);
    List<HandoffContextFieldDecisionPo> listFieldDecisions(
            @Param("tenantId") String tenantId, @Param("snapshotId") String snapshotId);

    int insertApproval(@Param("value") HandoffContextApprovalPo value);
    List<HandoffContextApprovalPo> listApprovals(
            @Param("tenantId") String tenantId, @Param("snapshotId") String snapshotId,
            @Param("limit") int limit);

    int insertReleaseEvidence(@Param("value") HandoffReleaseEvidencePo value);
    List<HandoffReleaseEvidencePo> listReleaseEvidence(
            @Param("tenantId") String tenantId, @Param("snapshotId") String snapshotId,
            @Param("limit") int limit);

    int insertResultSnapshot(@Param("value") ResultContextSnapshotPo value);
    ResultContextSnapshotPo findResultSnapshot(
            @Param("tenantId") String tenantId, @Param("resultSnapshotId") String resultSnapshotId);
    List<ResultContextSnapshotPo> listResultSnapshots(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId, @Param("limit") int limit);

    int insertAccessEvent(@Param("value") AgentContextAccessEventPo value);
    List<AgentContextAccessEventPo> listAccessEvents(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId,
            @Param("limit") int limit);
}
