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

/** Canonical P2.3B descriptor authority for Source System and Dispatch Flow configuration. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class BusinessConfigurationResourceDescriptorResolver implements ResourceDescriptorResolverPort, ResourceParticipantResolverPort {
    private final NamedParameterJdbcTemplate jdbc;
    public BusinessConfigurationResourceDescriptorResolver(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean supports(ResourceType type){return type==ResourceType.SOURCE_SYSTEM||type==ResourceType.DISPATCH_FLOW;}
    @Override public boolean supportsParticipants(ResourceType type){return supports(type);}

    @Override public Optional<ResourceDescriptor> resolve(ResourceRef ref,DescriptorResolutionContext context){
        if(!supports(ref.resourceType()))return Optional.empty();
        return ref.resourceType()==ResourceType.SOURCE_SYSTEM?source(ref,context):flow(ref,context);
    }
    @Override public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ref,DescriptorResolutionContext context){
        Optional<ResourceDescriptor> d=resolve(ref,context);if(d.isEmpty())return Optional.empty();
        List<ResourceParticipantProjection> p=participants(ref,d.get().ownership(),d.get().resourceVersion(),context.requestedAt());
        long rev=p.isEmpty()?0:Math.max(1,d.get().resourceVersion());
        return Optional.of(new ParticipantProjectionSnapshot(ref,rev,p,DescriptorAuthority.DISPATCH_CONFIGURATION,"",context.requestedAt()));
    }

    private Optional<ResourceDescriptor> source(ResourceRef ref,DescriptorResolutionContext context){
        try{return Optional.ofNullable(jdbc.queryForObject("""
            select source_system_id,display_name,status,owner_department_id,owner_group_id,coalesce(version,1) version,updated_at
            from source_systems where tenant_id=:tenantId and source_system_id=:id
            """,params(ref),(rs,n)->descriptor(ref,rs.getString("display_name"),rs.getString("owner_department_id"),rs.getString("owner_group_id"),null,
                    rs.getLong("version"),rs.getString("status"),rs.getObject("updated_at",OffsetDateTime.class),context.requestedAt())));}catch(EmptyResultDataAccessException e){return Optional.empty();}
    }
    private Optional<ResourceDescriptor> flow(ResourceRef ref,DescriptorResolutionContext context){
        try{return Optional.ofNullable(jdbc.queryForObject("""
            select flow_id,flow_code,status,source_system,owner_department_id,owner_group_id,coalesce(version,1) version,updated_at
            from dispatch_flows where tenant_id=:tenantId and flow_id=:id
            """,params(ref),(rs,n)->descriptor(ref,rs.getString("flow_code"),rs.getString("owner_department_id"),rs.getString("owner_group_id"),
                    new ResourceRef(ref.tenantId(),ResourceType.SOURCE_SYSTEM,rs.getString("source_system")),rs.getLong("version"),rs.getString("status"),
                    rs.getObject("updated_at",OffsetDateTime.class),context.requestedAt())));}catch(EmptyResultDataAccessException e){return Optional.empty();}
    }
    private ResourceDescriptor descriptor(ResourceRef ref,String key,String department,String group,ResourceRef parent,long version,String status,OffsetDateTime updated,Instant fallback){
        OwnershipDescriptor o=new OwnershipDescriptor(owner(department),owner(group),"","","","",Math.max(1,version));
        VisibilityDescriptor v=new VisibilityDescriptor(SensitivityLevel.INTERNAL,VisibilityLevel.STANDARD,"P2_3B_BUSINESS_SCOPE",PolicyVersion.ZERO);
        ResourceSecurityState state="RETIRED".equalsIgnoreCase(status)?ResourceSecurityState.ARCHIVED:ResourceSecurityState.NORMAL;
        String canonical=ref+"|"+key+"|"+o+"|"+status+"|"+version;
        return new ResourceDescriptor(ref,key,o,parent,parent,v,state,0,Math.max(1,version),DescriptorAuthority.DISPATCH_CONFIGURATION,hash(canonical),updated==null?fallback:updated.toInstant());
    }
    private List<ResourceParticipantProjection> participants(ResourceRef ref,OwnershipDescriptor o,long version,Instant at){
        List<ResourceParticipantProjection> out=new ArrayList<>();add(out,ref,ResourceParticipantType.DEPARTMENT,o.ownerDepartmentId(),version,at);add(out,ref,ResourceParticipantType.GROUP,o.ownerGroupId(),version,at);return List.copyOf(out);
    }
    private void add(List<ResourceParticipantProjection> out,ResourceRef ref,ResourceParticipantType type,String id,long version,Instant at){
        if(id==null||id.isBlank())return;
        List<String> permissions=ref.resourceType()==ResourceType.SOURCE_SYSTEM
                ? List.of("admin.source.system.list","admin.source.system.detail","admin.source.system.create","admin.source.system.update","admin.source.system.retire","admin.source.system.test","admin.source.system.mapping.manage","admin.source.system.credentials.read","admin.source.system.credentials.rotate")
                : List.of("admin.dispatch.flow.list","admin.dispatch.flow.detail","admin.dispatch.flow.update","admin.dispatch.flow.retire");
        out.add(new ResourceParticipantProjection("p2b-"+type+"-"+ref.resourceId()+"-"+id,ref,type,id,ResourceParticipantRole.OWNER,VisibilityLevel.STANDARD,permissions,at,null,DescriptorAuthority.DISPATCH_CONFIGURATION,Math.max(1,version),ResourceParticipantStatus.ACTIVE));
    }
    private MapSqlParameterSource params(ResourceRef ref){return new MapSqlParameterSource().addValue("tenantId",ref.tenantId()).addValue("id",ref.resourceId());}
    private static String owner(String v){return v==null?"":v.trim();}
    private static String hash(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
