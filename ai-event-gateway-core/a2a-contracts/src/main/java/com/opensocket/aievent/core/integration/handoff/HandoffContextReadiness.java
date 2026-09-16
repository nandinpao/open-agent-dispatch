package com.opensocket.aievent.core.integration.handoff;
import java.util.List;
public record HandoffContextReadiness(boolean ready,HandoffContextRequirement requirement,HandoffContextCheckpoint checkpoint,String snapshotId,Integer snapshotVersion,List<String> blockers,List<String> warnings) { public HandoffContextReadiness { blockers=blockers==null?List.of():List.copyOf(blockers); warnings=warnings==null?List.of():List.copyOf(warnings); } }
