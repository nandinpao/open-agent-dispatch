package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ResourceProjectionServiceTest {
    private static final Instant NOW=Instant.parse("2026-07-28T00:00:00Z");
    @Test void requiredOwnerMissingIsProjectedAsOrphaned(){
        ResourceRef ref=new ResourceRef("TENANT-A",ResourceType.ISSUE_CONNECTION,"CONN-1");
        ResourceDescriptor source=descriptor(ref,0,0,ResourceSecurityState.NORMAL,OwnershipDescriptor.unowned(1));
        CapturingRepository repository=new CapturingRepository();
        ResourceProjectionService service=service(ref,source,ParticipantProjectionSnapshot.empty(ref,DescriptorAuthority.ISSUE_TRACKING,NOW),repository);
        ResourceProjectionSyncResult result=service.project(ref,context(),"source-1");
        assertTrue(result.orphaned());assertEquals(ResourceSecurityState.ORPHANED,repository.batch.descriptor().securityState());
    }
    @Test void participantVersionDriftFailsBeforePersistence(){
        ResourceRef ref=new ResourceRef("TENANT-A",ResourceType.TASK,"TASK-1");
        ResourceDescriptor source=descriptor(ref,2,1,ResourceSecurityState.NORMAL,new OwnershipDescriptor("ERP","","","","","",1));
        ParticipantProjectionSnapshot participants=new ParticipantProjectionSnapshot(ref,1,List.of(),DescriptorAuthority.TASK_DOMAIN,"",NOW);
        CapturingRepository repository=new CapturingRepository();
        ResourceProjectionService service=service(ref,source,participants,repository);
        assertEquals("RESOURCE_PARTICIPANT_VERSION_MISMATCH",assertThrows(IllegalStateException.class,()->service.project(ref,context(),"source-1")).getMessage());
        assertNull(repository.batch);
    }
    private ResourceProjectionService service(ResourceRef ref,ResourceDescriptor descriptor,ParticipantProjectionSnapshot participants,CapturingRepository repository){
        ResourceDescriptorResolverPort descriptorPort=new ResourceDescriptorResolverPort(){public boolean supports(ResourceType type){return type==ref.resourceType();}public Optional<ResourceDescriptor> resolve(ResourceRef ignored,DescriptorResolutionContext context){return Optional.of(descriptor);}};
        ResourceParticipantResolverPort participantPort=new ResourceParticipantResolverPort(){public boolean supportsParticipants(ResourceType type){return type==ref.resourceType();}public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ignored,DescriptorResolutionContext context){return Optional.of(participants);}};
        return new ResourceProjectionService(new AuthoritativeResourceDescriptorService(new DefaultResourceCatalog(),List.of(descriptorPort)),new AuthoritativeResourceParticipantService(List.of(participantPort)),repository,new OwnershipRequirementPolicy());
    }
    private ResourceDescriptor descriptor(ResourceRef ref,long participantVersion,long resourceVersion,ResourceSecurityState state,OwnershipDescriptor ownership){
        VisibilityDescriptor visibility=new VisibilityDescriptor(SensitivityLevel.RESTRICTED,VisibilityLevel.SENSITIVE,"TEST",new PolicyVersion(1,1,"hash"));
        return new ResourceDescriptor(ref,ref.resourceId(),ownership,null,null,visibility,state,participantVersion,resourceVersion,ref.resourceType()==ResourceType.TASK?DescriptorAuthority.TASK_DOMAIN:DescriptorAuthority.ISSUE_TRACKING,"source-hash",NOW);
    }
    private DescriptorResolutionContext context(){return new DescriptorResolutionContext("corr-1","test",NOW);}
    private static final class CapturingRepository implements ResourceProjectionRepository {
        private ResourceProjectionBatch batch;
        public Optional<ResourceDescriptor> findDescriptor(ResourceRef ref){return Optional.empty();}
        public ResourceProjectionSyncResult synchronize(ResourceProjectionBatch value){batch=value;ResourceDescriptor d=value.descriptor();return new ResourceProjectionSyncResult(d.resourceRef(),true,d.securityState()==ResourceSecurityState.ORPHANED,"",d.descriptorHash(),d.resourceVersion(),d.participantVersion(),value.projectedAt());}
        public List<ResourceRef> findStaleDescriptors(Instant before,int limit){return List.of();}
        public void recordReconciliation(ResourceReconciliationRecord record){}
        public Optional<OwnershipTransferResult> findOwnershipTransfer(ResourceRef ref,String key){return Optional.empty();}
        public void recordOwnershipTransfer(OwnershipTransferCommand command,OwnershipTransferResult result){}
        public Optional<ResourceOrphanRepairCase> findOpenOrphan(ResourceRef ref){return Optional.empty();}
        public void markOrphanResolved(String repairId,ResourceRef ref,OwnershipDescriptor ownership,long version,String actor,Instant at){}
    }
}
