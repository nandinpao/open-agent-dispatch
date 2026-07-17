package com.opensocket.aievent.database.persistence.adapter.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;
import com.opensocket.aievent.database.persistence.adapter.po.AdapterExecutorAuditPo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix="adapter-executor.audit", name="store", havingValue="MYBATIS")
public class AdapterExecutorAuditPersistenceConverter {
    private final ObjectMapper objectMapper;

    public AdapterExecutorAuditPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AdapterExecutorAuditPo toPo(AdapterExecutorAuditRecord a){AdapterExecutorAuditPo r=new AdapterExecutorAuditPo();r.setAuditId(a.getAuditId());r.setActionId(a.getActionId());r.setTaskId(a.getTaskId());r.setIncidentId(a.getIncidentId());r.setAdapterType(a.getAdapterType());r.setActionType(a.getActionType());r.setExecutorName(a.getExecutorName());r.setBeforeStatus(a.getBeforeStatus());r.setAfterStatus(a.getAfterStatus());r.setOutcome(a.getOutcome());r.setMessage(a.getMessage());r.setAttemptCount(a.getAttemptCount());r.setCreatedAt(a.getCreatedAt());r.setPayloadSnapshotJson(write(a.getPayloadSnapshot()==null?Map.of():a.getPayloadSnapshot()));return r;}

    public AdapterExecutorAuditRecord toDomain(AdapterExecutorAuditPo r){AdapterExecutorAuditRecord a=new AdapterExecutorAuditRecord();a.setAuditId(r.getAuditId());a.setActionId(r.getActionId());a.setTaskId(r.getTaskId());a.setIncidentId(r.getIncidentId());a.setAdapterType(r.getAdapterType());a.setActionType(r.getActionType());a.setExecutorName(r.getExecutorName());a.setBeforeStatus(r.getBeforeStatus());a.setAfterStatus(r.getAfterStatus());a.setOutcome(r.getOutcome());a.setMessage(r.getMessage());a.setAttemptCount(r.getAttemptCount());a.setCreatedAt(r.getCreatedAt());a.setPayloadSnapshot(read(r.getPayloadSnapshotJson()));return a;}

    public String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot serialize adapter executor audit payload",ex);}}

    public Map<String,Object> read(String json){try{if(json==null||json.isBlank())return new LinkedHashMap<>();return objectMapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception ex){throw new IllegalStateException("Cannot deserialize adapter executor audit payload",ex);}}
}
