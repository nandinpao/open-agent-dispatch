package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.List;
import java.util.Map;
/** Authorizes an export; actual row production remains in the owning business module. */
public record ResourceExportCommand(ResourceRef resourceRef,ResourceExportFormat format,List<String> requestedFields,long estimatedRowCount,String purpose,String idempotencyKey,String correlationId,Map<String,String> trustedFlowContext){
 public ResourceExportCommand{if(resourceRef==null||format==null)throw new IllegalArgumentException("resourceRef and format are required");requestedFields=requestedFields==null?List.of():requestedFields.stream().map(String::trim).filter(v->!v.isBlank()).distinct().toList();if(estimatedRowCount<0)throw new IllegalArgumentException("estimatedRowCount must be non-negative");purpose=required(purpose,"purpose");idempotencyKey=required(idempotencyKey,"idempotencyKey");correlationId=required(correlationId,"correlationId");trustedFlowContext=trustedFlowContext==null?Map.of():Map.copyOf(trustedFlowContext);}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
