package com.opensocket.aievent.core.resourceaccess.runtime;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** RS2 canonical descriptor authority for source-derived Business Events and Incidents. */
@Component
@ConditionalOnProperty(prefix="resource-access", name="enabled", havingValue="true")
public final class EventProcessingResourceDescriptorResolver implements ResourceDescriptorResolverPort, ResourceParticipantResolverPort {
    private final NamedParameterJdbcTemplate jdbc;
    public EventProcessingResourceDescriptorResolver(NamedParameterJdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}
    @Override public boolean supports(ResourceType type){return type==ResourceType.EVENT||type==ResourceType.INCIDENT;}
    @Override public boolean supportsParticipants(ResourceType type){return supports(type);}

    @Override public Optional<ResourceDescriptor> resolve(ResourceRef ref, DescriptorResolutionContext context){
        if(!supports(ref.resourceType())) return Optional.empty();
        return ref.resourceType()==ResourceType.EVENT ? event(ref,context) : incident(ref,context);
    }

    @Override public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ref, DescriptorResolutionContext context){
        Optional<ResourceDescriptor> descriptor=resolve(ref,context);
        if(descriptor.isEmpty()) return Optional.empty();
        ResourceDescriptor d=descriptor.get();
        List<ResourceParticipantProjection> participants=new ArrayList<>();
        add(participants,ref,ResourceParticipantType.DEPARTMENT,d.ownership().ownerDepartmentId(),d.resourceVersion(),context.requestedAt());
        add(participants,ref,ResourceParticipantType.GROUP,d.ownership().ownerGroupId(),d.resourceVersion(),context.requestedAt());
        return Optional.of(new ParticipantProjectionSnapshot(ref,Math.max(1,d.resourceVersion()),participants,
                DescriptorAuthority.EVENT_PROCESSING,"RS2_SOURCE_SCOPE_SNAPSHOT",context.requestedAt()));
    }

    private Optional<ResourceDescriptor> event(ResourceRef ref, DescriptorResolutionContext context){
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                select event_id,source_system,event_type,owner_department_id,owner_group_id,scope_status,
                       coalesce(scope_source_version,1) resource_version,coalesce(scope_inherited_at,decided_at) resolved_at
                  from event_decisions
                 where tenant_id=:tenantId and event_id=:id
                """, params(ref), (rs,n)->descriptor(ref,
                    text(rs.getString("event_type"),ref.resourceId()),rs.getString("source_system"),
                    rs.getString("owner_department_id"),rs.getString("owner_group_id"),rs.getString("scope_status"),
                    rs.getLong("resource_version"),rs.getObject("resolved_at", OffsetDateTime.class),context.requestedAt())));
        } catch (EmptyResultDataAccessException ex){return Optional.empty();}
    }

    private Optional<ResourceDescriptor> incident(ResourceRef ref, DescriptorResolutionContext context){
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                select incident_id,source_system,event_type,owner_department_id,owner_group_id,scope_status,
                       coalesce(scope_source_version,1) resource_version,coalesce(scope_inherited_at,last_seen_at) resolved_at
                  from incidents
                 where tenant_id=:tenantId and incident_id=:id
                """, params(ref), (rs,n)->descriptor(ref,
                    text(rs.getString("event_type"),ref.resourceId()),rs.getString("source_system"),
                    rs.getString("owner_department_id"),rs.getString("owner_group_id"),rs.getString("scope_status"),
                    rs.getLong("resource_version"),rs.getObject("resolved_at", OffsetDateTime.class),context.requestedAt())));
        } catch (EmptyResultDataAccessException ex){return Optional.empty();}
    }

    private ResourceDescriptor descriptor(ResourceRef ref,String key,String sourceSystem,String department,String group,String scopeStatus,
            long version,OffsetDateTime resolvedAt,Instant fallback){
        long safeVersion=Math.max(1,version);
        OwnershipDescriptor ownership=new OwnershipDescriptor(owner(department),owner(group),"","","","",safeVersion);
        VisibilityDescriptor visibility=new VisibilityDescriptor(
                ref.resourceType()==ResourceType.EVENT?SensitivityLevel.CONFIDENTIAL:SensitivityLevel.INTERNAL,
                VisibilityLevel.STANDARD,"RS2_SOURCE_SCOPE_SNAPSHOT",PolicyVersion.ZERO);
        ResourceSecurityState state="UNRESOLVED".equalsIgnoreCase(scopeStatus)?ResourceSecurityState.ORPHANED:ResourceSecurityState.NORMAL;
        ResourceRef source=(sourceSystem==null||sourceSystem.isBlank())?null:new ResourceRef(ref.tenantId(),ResourceType.SOURCE_SYSTEM,sourceSystem);
        String canonical=ref+"|"+key+"|"+sourceSystem+"|"+ownership+"|"+scopeStatus+"|"+safeVersion;
        return new ResourceDescriptor(ref,key,ownership,source,source,visibility,state,0,safeVersion,
                DescriptorAuthority.EVENT_PROCESSING,hash(canonical),resolvedAt==null?fallback:resolvedAt.toInstant());
    }

    private void add(List<ResourceParticipantProjection> out,ResourceRef ref,ResourceParticipantType type,String id,long version,Instant at){
        if(id==null||id.isBlank()) return;
        List<String> permissions=ref.resourceType()==ResourceType.EVENT
                ? List.of("admin.business.event.list","admin.business.event.detail","admin.business.event.payload.read","admin.business.event.export","admin.business.event.replay")
                : List.of("api.incident.incidents","api.incident.incident","api.incident.occurrence.summary","api.incident.resolve","api.incident.reopen","api.incident.suppress");
        out.add(new ResourceParticipantProjection("rs2-"+type+"-"+ref.resourceId()+"-"+id,ref,type,id,
                ResourceParticipantRole.OWNER,VisibilityLevel.STANDARD,permissions,at,null,
                DescriptorAuthority.EVENT_PROCESSING,Math.max(1,version),ResourceParticipantStatus.ACTIVE));
    }

    private MapSqlParameterSource params(ResourceRef ref){return new MapSqlParameterSource().addValue("tenantId",ref.tenantId()).addValue("id",ref.resourceId());}
    private static String owner(String value){return value==null?"":value.trim();}
    private static String text(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private static String hash(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
