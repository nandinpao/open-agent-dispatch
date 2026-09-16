package com.opensocket.aievent.core.capability;
import java.util.List;
/** Semantic fast-path lookup input. No Provider/Agent/Pool/transport fields are permitted. */
public record FastPathResolutionRequest(String classification,List<CapabilityRequirement> requiredCapabilities){public FastPathResolutionRequest{requiredCapabilities=requiredCapabilities==null?List.of():List.copyOf(requiredCapabilities);}}
