package com.opensocket.aievent.core.integration.identity;
public record MappingResolutionRequest(String tenantId,String connectionId,String departmentId,String groupId,String serviceDomainId,String sourceSystemId,String taskType) {}
