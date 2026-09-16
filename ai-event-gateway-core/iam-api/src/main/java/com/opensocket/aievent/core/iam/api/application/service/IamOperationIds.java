package com.opensocket.aievent.core.iam.api.application.service;
import java.nio.charset.StandardCharsets;import java.util.UUID;
final class IamOperationIds {private IamOperationIds(){}static String eventId(String operation,String scope,String key){return "evt-"+UUID.nameUUIDFromBytes((operation+"|"+scope+"|"+key).getBytes(StandardCharsets.UTF_8));}static String resourceId(String prefix,String operation,String scope,String key){return prefix+"-"+UUID.nameUUIDFromBytes((operation+"|"+scope+"|"+key).getBytes(StandardCharsets.UTF_8));}}
