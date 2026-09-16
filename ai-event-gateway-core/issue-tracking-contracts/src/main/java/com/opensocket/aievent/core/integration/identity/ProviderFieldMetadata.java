package com.opensocket.aievent.core.integration.identity;
import java.util.List;
public record ProviderFieldMetadata(String fieldId,String fieldKey,String displayName,String fieldType,boolean required,List<String> allowedValues) {
 public ProviderFieldMetadata { allowedValues=allowedValues==null?List.of():List.copyOf(allowedValues); }
}
