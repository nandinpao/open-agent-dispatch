package com.opensocket.aievent.core.integration.identity;
import java.util.*;
public record ProjectMappingDiff(String mappingId,int fromVersion,int toVersion,Map<String,String> changes) { public ProjectMappingDiff { changes=changes==null?Map.of():Map.copyOf(changes); } }
