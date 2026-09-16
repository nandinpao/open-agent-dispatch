package com.opensocket.aievent.database.persistence.eventprocessing.converter;

import java.util.*;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.decision.*;
import com.opensocket.aievent.database.persistence.eventprocessing.po.EventDecisionPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix="event.decisions", name="store", havingValue="MYBATIS")
public class EventDecisionPersistenceConverter {
    private final ObjectMapper objectMapper;
    public EventDecisionPersistenceConverter(ObjectMapper objectMapper){this.objectMapper=objectMapper;}
    public EventDecisionPo toPo(EventDecisionRecord r){
        EventDecisionPo p=new EventDecisionPo(); p.setEventId(r.getEventId()); p.setTenantId(r.getTenantId()); p.setSourceSystem(r.getSourceSystem());
        p.setEventType(r.getEventType()); p.setEventStage(r.getEventStage()); p.setCorrelationId(r.getCorrelationId()); p.setNormalizedMessage(r.getNormalizedMessage());
        p.setPayloadJson(write(r.getPayload()==null?Map.of():r.getPayload())); p.setOccurredAt(r.getOccurredAt()); p.setFingerprint(r.getFingerprint()); p.setIncidentId(r.getIncidentId());
        p.setDecisionType(r.getDecisionType()==null?null:r.getDecisionType().name()); p.setDuplicate(r.isDuplicate()); p.setOccurrenceCount(r.getOccurrenceCount());
        p.setActionsJson(write(r.getActions()==null?List.of():r.getActions().stream().map(Enum::name).toList())); p.setReason(r.getReason()); p.setDecidedAt(r.getDecidedAt());
        p.setOwnerDepartmentId(r.getOwnerDepartmentId()); p.setOwnerGroupId(r.getOwnerGroupId()); p.setScopeStatus(r.getScopeStatus()); p.setScopeSourceVersion(r.getScopeSourceVersion()); p.setScopeInheritedAt(r.getScopeInheritedAt());
        return p;
    }
    public EventDecisionRecord toDomain(EventDecisionPo p){
        EventDecisionRecord r=new EventDecisionRecord(); r.setEventId(p.getEventId()); r.setTenantId(p.getTenantId()); r.setSourceSystem(p.getSourceSystem()); r.setEventType(p.getEventType());
        r.setEventStage(p.getEventStage()); r.setCorrelationId(p.getCorrelationId()); r.setNormalizedMessage(p.getNormalizedMessage()); r.setPayload(readPayload(p.getPayloadJson())); r.setOccurredAt(p.getOccurredAt());
        r.setFingerprint(p.getFingerprint()); r.setIncidentId(p.getIncidentId()); r.setDecisionType(p.getDecisionType()==null?null:DecisionType.valueOf(p.getDecisionType())); r.setDuplicate(p.isDuplicate());
        r.setOccurrenceCount(p.getOccurrenceCount()); r.setActions(readActions(p.getActionsJson())); r.setReason(p.getReason()); r.setDecidedAt(p.getDecidedAt()); r.setOwnerDepartmentId(p.getOwnerDepartmentId());
        r.setOwnerGroupId(p.getOwnerGroupId()); r.setScopeStatus(p.getScopeStatus()); r.setScopeSourceVersion(p.getScopeSourceVersion()); r.setScopeInheritedAt(p.getScopeInheritedAt()); return r;
    }
    public String write(Object v){try{return objectMapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException("Cannot serialize event decision data",e);}}
    public List<DecisionAction> readActions(String json){try{if(json==null||json.isBlank())return List.of();return Arrays.stream(objectMapper.readValue(json,String[].class)).map(DecisionAction::valueOf).toList();}catch(Exception e){throw new IllegalStateException("Cannot deserialize event decision actions",e);}}
    @SuppressWarnings("unchecked") public Map<String,Object> readPayload(String json){try{if(json==null||json.isBlank())return Map.of();return objectMapper.readValue(json,Map.class);}catch(Exception e){throw new IllegalStateException("Cannot deserialize event payload",e);}}
}
