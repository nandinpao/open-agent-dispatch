package com.opensocket.aievent.core.routing.governance.routing;

import java.util.LinkedHashMap;
import java.util.Map;

public record GenericRoutingScore(int score, Map<String,Object> breakdown) {
    public GenericRoutingScore { score=Math.max(0,Math.min(100,score)); breakdown=breakdown==null?Map.of():new LinkedHashMap<>(breakdown); }
}
