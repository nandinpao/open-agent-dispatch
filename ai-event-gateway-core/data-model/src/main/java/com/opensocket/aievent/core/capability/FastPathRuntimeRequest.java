package com.opensocket.aievent.core.capability;
import java.util.List;
/** Runtime bootstrap input contains semantic WHAT only; no Provider/Agent/Pool/transport target may be supplied. */
public record FastPathRuntimeRequest(String taskRef,String classification,List<CapabilityRequirement> requiredCapabilities,List<String> contextRefs,String idempotencyKey){public FastPathRuntimeRequest{requiredCapabilities=requiredCapabilities==null?List.of():List.copyOf(requiredCapabilities);contextRefs=contextRefs==null?List.of():List.copyOf(contextRefs);}}
