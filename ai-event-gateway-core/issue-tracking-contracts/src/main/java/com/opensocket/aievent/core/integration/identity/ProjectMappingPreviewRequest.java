package com.opensocket.aievent.core.integration.identity;
import java.util.Map;
public record ProjectMappingPreviewRequest(Map<String,Object> context) { public ProjectMappingPreviewRequest { context=context==null?Map.of():Map.copyOf(context); } }
