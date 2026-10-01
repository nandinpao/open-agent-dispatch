package com.opensocket.aievent.core.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.kernel.configuration.ConfigurationScope;
import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationAuditEntry;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationConfigSet;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevision;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionItem;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionState;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;

/**
 * V40-3 transactional revision authority.
 * This service persists governed revisions only; it does not distribute values or claim runtime apply success.
 */
@Service
public class RuntimeConfigurationRevisionService {
    private final RuntimeConfigurationRevisionStore store;

    public RuntimeConfigurationRevisionService(RuntimeConfigurationRevisionStore store) {
        this.store=store;
    }

    @Transactional
    public RuntimeConfigurationConfigSet createSet(String setKey,OpenDispatchEnvironment environment,ConfigurationScope scope,
            String scopeRef,String ownerComponent,String actor) {
        return store.createConfigSet(UUID.randomUUID().toString(),setKey,environment,scope,scopeRef,ownerComponent,actor);
    }

    @Transactional
    public RuntimeConfigurationRevision createDraft(String configSetId,String actor,String reason,String correlationId) {
        return store.createDraft(UUID.randomUUID().toString(),configSetId,actor,reason,null,null,correlationId);
    }

    @Transactional
    public RuntimeConfigurationRevisionItem putValue(String revisionId,String definitionKey,String canonicalJson,
            String actor,String reason,String correlationId) {
        return store.putDraftValue(revisionId,definitionKey,canonicalJson,sha256(canonicalJson),actor,reason,correlationId);
    }

    @Transactional
    public RuntimeConfigurationRevision validate(String revisionId,String actor,String reason,String correlationId) {
        RuntimeConfigurationRevision revision=require(revisionId);
        if(store.listItems(revisionId).isEmpty()) throw new IllegalStateException("Cannot validate an empty configuration revision");
        return store.transition(revisionId,RuntimeConfigurationRevisionState.DRAFT,RuntimeConfigurationRevisionState.VALIDATED,actor,reason,correlationId);
    }

    @Transactional
    public RuntimeConfigurationRevision submitForApproval(String revisionId,String actor,String reason,String correlationId) {
        return store.transition(revisionId,RuntimeConfigurationRevisionState.VALIDATED,RuntimeConfigurationRevisionState.PENDING_APPROVAL,actor,reason,correlationId);
    }

    /** Approval is separation-of-duty governed from V40-7 onward. */
    @Transactional
    public RuntimeConfigurationRevision approve(String revisionId,String actor,String reason,String correlationId) {
        RuntimeConfigurationRevision revision=require(revisionId);
        if(revision.state()!=RuntimeConfigurationRevisionState.PENDING_APPROVAL) throw new IllegalStateException("Only PENDING_APPROVAL revisions may be approved");
        if(actor.equals(revision.createdBy()) || actor.equals(revision.submittedBy())) throw new IllegalStateException("RUNTIME_CONFIG_SELF_APPROVAL_DENIED");
        return store.transition(revisionId,RuntimeConfigurationRevisionState.PENDING_APPROVAL,RuntimeConfigurationRevisionState.APPROVED,actor,reason,correlationId);
    }

    @Transactional
    public RuntimeConfigurationRevision reject(String revisionId,String actor,String reason,String correlationId) {
        RuntimeConfigurationRevision revision=require(revisionId);
        if(revision.state()!=RuntimeConfigurationRevisionState.PENDING_APPROVAL) throw new IllegalStateException("Only PENDING_APPROVAL revisions may be rejected");
        return store.transition(revisionId,RuntimeConfigurationRevisionState.PENDING_APPROVAL,RuntimeConfigurationRevisionState.REJECTED,actor,reason,correlationId);
    }

    @Transactional
    public RuntimeConfigurationRevision publish(String revisionId,String expectedBaseRevisionId,String actor,String reason,String correlationId) {
        return store.publish(revisionId,expectedBaseRevisionId,actor,reason,correlationId);
    }

    /** Rollback is append-only: create a new DRAFT from a prior published revision. */
    @Transactional
    public RuntimeConfigurationRevision createRollbackDraft(String configSetId,String restoreSourceRevisionId,
            String actor,String reason,String correlationId) {
        return store.createRollbackDraft(UUID.randomUUID().toString(),configSetId,restoreSourceRevisionId,actor,reason,correlationId);
    }

    @Transactional(readOnly=true) public List<RuntimeConfigurationRevisionItem> items(String revisionId){return store.listItems(revisionId);}
    @Transactional(readOnly=true) public List<RuntimeConfigurationAuditEntry> history(String configSetId,int limit){return store.history(configSetId,limit);}

    private RuntimeConfigurationRevision require(String revisionId){return store.findRevision(revisionId).orElseThrow(()->new IllegalArgumentException("Revision not found: "+revisionId));}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
