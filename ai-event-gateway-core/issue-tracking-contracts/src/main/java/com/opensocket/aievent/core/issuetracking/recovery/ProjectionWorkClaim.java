package com.opensocket.aievent.core.issuetracking.recovery;
import java.time.OffsetDateTime;
public record ProjectionWorkClaim(OrderedProjectionWork work,String claimToken,OffsetDateTime claimUntil) { public ProjectionWorkClaim { if(work==null||claimToken==null||claimToken.isBlank()||claimUntil==null) throw new IllegalArgumentException("Invalid projection work claim."); } }
