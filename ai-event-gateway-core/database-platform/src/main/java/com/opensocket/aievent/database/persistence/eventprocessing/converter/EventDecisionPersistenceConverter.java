package com.opensocket.aievent.database.persistence.eventprocessing.converter;

import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.decision.DecisionAction;
import com.opensocket.aievent.core.decision.DecisionType;
import com.opensocket.aievent.core.decision.EventDecisionRecord;
import com.opensocket.aievent.database.persistence.eventprocessing.po.EventDecisionPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix="event.decisions", name="store", havingValue="MYBATIS")
public class EventDecisionPersistenceConverter {
    private final ObjectMapper objectMapper;

    public EventDecisionPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventDecisionPo toPo(EventDecisionRecord r){EventDecisionPo po=new EventDecisionPo();po.setEventId(r.getEventId());po.setFingerprint(r.getFingerprint());po.setIncidentId(r.getIncidentId());po.setDecisionType(r.getDecisionType()==null?null:r.getDecisionType().name());po.setDuplicate(r.isDuplicate());po.setOccurrenceCount(r.getOccurrenceCount());po.setActionsJson(write(r.getActions()==null?List.of():r.getActions().stream().map(Enum::name).toList()));po.setReason(r.getReason());po.setDecidedAt(r.getDecidedAt());return po;}

    public EventDecisionRecord toDomain(EventDecisionPo po){EventDecisionRecord r=new EventDecisionRecord();r.setEventId(po.getEventId());r.setFingerprint(po.getFingerprint());r.setIncidentId(po.getIncidentId());r.setDecisionType(po.getDecisionType()==null?null:DecisionType.valueOf(po.getDecisionType()));r.setDuplicate(po.isDuplicate());r.setOccurrenceCount(po.getOccurrenceCount());r.setActions(readActions(po.getActionsJson()));r.setReason(po.getReason());r.setDecidedAt(po.getDecidedAt());return r;}

    public String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot serialize event decision actions",ex);}}

    public List<DecisionAction> readActions(String json){try{if(json==null||json.isBlank())return List.of();return Arrays.stream(objectMapper.readValue(json,String[].class)).map(DecisionAction::valueOf).toList();}catch(Exception ex){throw new IllegalStateException("Cannot deserialize event decision actions",ex);}}
}
