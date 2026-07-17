package com.opensocket.aievent.database.persistence.task.converter;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.task.po.RoutingDecisionPo;
import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.DispatchUserFacingError;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix="routing", name="decision-store", havingValue="MYBATIS")
public class RoutingDecisionPersistenceConverter {
    private final ObjectMapper objectMapper;

    public RoutingDecisionPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RoutingDecisionPo toPo(RoutingDecisionRecord d){RoutingDecisionPo r=new RoutingDecisionPo();r.setDecisionId(d.getDecisionId());r.setTaskId(d.getTaskId());r.setIncidentId(d.getIncidentId());r.setRoutingPolicy(d.getRoutingPolicy()==null?null:d.getRoutingPolicy().name());r.setStatus(d.getStatus()==null?null:d.getStatus().name());r.setSelectedAgentId(d.getSelectedAgentId());r.setSelectedGatewayNodeId(d.getSelectedGatewayNodeId());r.setSelectedAgentSessionId(d.getSelectedAgentSessionId());r.setSelectedSiteId(d.getSelectedSiteId());r.setSelectedScore(d.getSelectedScore());r.setDecisionReason(d.getDecisionReason());r.setUserFacingErrorJson(d.getUserFacingError()==null?null:write(d.getUserFacingError()));r.setCandidatesJson(write(d.getCandidates()==null?List.of():d.getCandidates()));r.setCreatedAt(d.getCreatedAt());return r;}

    public RoutingDecisionRecord toDomain(RoutingDecisionPo r){RoutingDecisionRecord d=new RoutingDecisionRecord();d.setDecisionId(r.getDecisionId());d.setTaskId(r.getTaskId());d.setIncidentId(r.getIncidentId());d.setRoutingPolicy(parseRoutingPolicy(r.getRoutingPolicy()));d.setStatus(r.getStatus()==null?null:RoutingDecisionStatus.valueOf(r.getStatus()));d.setSelectedAgentId(r.getSelectedAgentId());d.setSelectedGatewayNodeId(r.getSelectedGatewayNodeId());d.setSelectedAgentSessionId(r.getSelectedAgentSessionId());d.setSelectedSiteId(r.getSelectedSiteId());d.setSelectedScore(r.getSelectedScore());d.setDecisionReason(r.getDecisionReason());d.setUserFacingError(readUserFacingError(r.getUserFacingErrorJson()));d.setCandidates(read(r.getCandidatesJson()));d.setCreatedAt(r.getCreatedAt());return d;}

    private RoutingPolicy parseRoutingPolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return RoutingPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Historical rows may contain a retired source-specific policy name.
            // Keep reads fail-closed without reintroducing that name into the enum.
            return RoutingPolicy.MANUAL_REVIEW;
        }
    }

    public String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot serialize routing candidates",ex);}}

    public List<AgentCandidateScore> read(String json){try{if(json==null||json.isBlank())return List.of();return objectMapper.readValue(json,new TypeReference<List<AgentCandidateScore>>(){});}catch(Exception ex){throw new IllegalStateException("Cannot deserialize routing candidates",ex);}}

    public DispatchUserFacingError readUserFacingError(String json){try{if(json==null||json.isBlank())return null;return objectMapper.readValue(json,DispatchUserFacingError.class);}catch(Exception ex){throw new IllegalStateException("Cannot deserialize routing user-facing error",ex);}}
}
