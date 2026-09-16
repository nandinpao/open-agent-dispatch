package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Map;
/** Request for a governed attachment download grant. The caller cannot provide storage or Provider identity. */
public record AttachmentDownloadCommand(ResourceRef attachmentRef,String purpose,String correlationId,Map<String,String> trustedFlowContext){
 public AttachmentDownloadCommand{if(attachmentRef==null)throw new IllegalArgumentException("attachmentRef is required");purpose=required(purpose,"purpose");correlationId=required(correlationId,"correlationId");trustedFlowContext=trustedFlowContext==null?Map.of():Map.copyOf(trustedFlowContext);}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
