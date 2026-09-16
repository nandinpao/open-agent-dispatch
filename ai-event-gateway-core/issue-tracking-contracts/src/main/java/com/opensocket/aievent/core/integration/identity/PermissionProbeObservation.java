package com.opensocket.aievent.core.integration.identity;
import java.util.List; import java.util.Map;
public record PermissionProbeObservation(Map<IntegrationPermissionCapability,PermissionProbeResultStatus> capabilities,List<String> elevatedPermissions,String summary) { public PermissionProbeObservation { capabilities=capabilities==null?Map.of():Map.copyOf(capabilities); elevatedPermissions=elevatedPermissions==null?List.of():List.copyOf(elevatedPermissions); } }
