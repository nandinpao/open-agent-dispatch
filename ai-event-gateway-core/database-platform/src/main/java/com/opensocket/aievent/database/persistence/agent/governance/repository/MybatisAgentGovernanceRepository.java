package com.opensocket.aievent.database.persistence.agent.governance.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.agent.governance.AgentApprovalAuditEntry;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationScope;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentCredential;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceRepository;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceTableDiagnostic;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementPolicy;
import com.opensocket.aievent.database.persistence.agent.governance.converter.AgentGovernancePersistenceConverter;
import com.opensocket.aievent.database.persistence.agent.dao.AgentGovernanceDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "agent-governance", name = "store", havingValue = "MYBATIS")
public class MybatisAgentGovernanceRepository implements AgentGovernanceRepository {
    private final AgentGovernanceDao dao;
    private final AgentGovernancePersistenceConverter converter;

    public MybatisAgentGovernanceRepository(AgentGovernanceDao dao, AgentGovernancePersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public AgentEnrollmentRequest saveEnrollment(AgentEnrollmentRequest enrollment) {
        dao.upsertEnrollment(converter.toPo(enrollment));
        return enrollment;
    }

    @Override
    public Optional<AgentEnrollmentRequest> findEnrollment(String enrollmentId) {
        return Optional.ofNullable(dao.findEnrollment(enrollmentId)).map(converter::toEnrollment);
    }

    @Override
    public Optional<AgentEnrollmentRequest> findLatestEnrollmentByAgent(String claimedAgentId) {
        return Optional.ofNullable(dao.findLatestEnrollmentByAgent(claimedAgentId)).map(converter::toEnrollment);
    }

    @Override
    public List<AgentEnrollmentRequest> searchEnrollments(AgentEnrollmentStatus status, int limit) {
        return dao.searchEnrollments(status == null ? null : status.name(), limit).stream().map(converter::toEnrollment).toList();
    }

    @Override
    public AgentProfile saveProfile(AgentProfile profile) {
        dao.upsertProfile(converter.toPo(profile));
        return profile;
    }

    @Override
    public Optional<AgentProfile> findProfile(String agentId) {
        return Optional.ofNullable(dao.findProfile(agentId)).map(converter::toProfile);
    }

    @Override
    public List<AgentProfile> searchProfiles(AgentApprovalStatus approvalStatus, int limit) {
        return dao.searchProfiles(approvalStatus == null ? null : approvalStatus.name(), limit).stream().map(converter::toProfile).toList();
    }

    @Override
    public AgentCredential saveCredential(AgentCredential credential) {
        dao.insertCredential(converter.toPo(credential));
        return credential;
    }

    @Override
    public Optional<AgentCredential> findActiveCredentialByTokenHash(String agentId, String tokenHash, OffsetDateTime now) {
        return Optional.ofNullable(dao.findActiveCredentialByTokenHash(agentId, tokenHash, now)).map(converter::toCredential);
    }

    @Override
    public Optional<AgentCredential> findActiveCredentialByFingerprint(String agentId, String publicKeyFingerprint, OffsetDateTime now) {
        return Optional.ofNullable(dao.findActiveCredentialByFingerprint(agentId, publicKeyFingerprint, now)).map(converter::toCredential);
    }

    @Override
    public Optional<AgentCredential> findLatestCredential(String agentId) {
        return Optional.ofNullable(dao.findLatestCredential(agentId)).map(converter::toCredential);
    }

    @Override
    public int revokeCredentials(String agentId, String reason, OffsetDateTime revokedAt) {
        return dao.revokeCredentials(agentId, reason, revokedAt);
    }

    @Override
    public List<AgentCapability> replaceCapabilities(String agentId, List<AgentCapability> capabilities) {
        dao.deleteCapabilities(agentId);
        if (capabilities != null) {
            capabilities.forEach(capability -> dao.insertCapability(converter.toPo(capability)));
        }
        return findEnabledCapabilities(agentId);
    }

    @Override
    public List<AgentCapability> findEnabledCapabilities(String agentId) {
        return dao.findEnabledCapabilities(agentId).stream().map(converter::toCapability).toList();
    }

    @Override
    public List<AgentAuthorizationScope> replaceScopes(String agentId, List<AgentAuthorizationScope> scopes) {
        dao.deleteScopes(agentId);
        if (scopes != null) {
            scopes.forEach(scope -> dao.insertScope(converter.toPo(scope)));
        }
        return findEnabledScopes(agentId);
    }

    @Override
    public List<AgentAuthorizationScope> findEnabledScopes(String agentId) {
        return dao.findEnabledScopes(agentId).stream().map(converter::toScope).toList();
    }

    @Override
    public AgentApprovalAuditEntry appendAudit(AgentApprovalAuditEntry audit) {
        dao.insertAudit(converter.toPo(audit));
        return audit;
    }

    @Override
    public AgentSecurityEvent saveSecurityEvent(AgentSecurityEvent event) {
        dao.insertSecurityEvent(converter.toPo(event));
        return event;
    }

    @Override
    public List<AgentSecurityEvent> searchSecurityEvents(String agentId, int limit) {
        return dao.searchSecurityEvents(agentId, limit).stream().map(converter::toSecurityEvent).toList();
    }


    @Override
    public AgentSecurityEnforcementPolicy saveSecurityEnforcementPolicy(AgentSecurityEnforcementPolicy policy) {
        dao.upsertSecurityEnforcementPolicy(converter.toPo(policy));
        return policy;
    }

    @Override
    public Optional<AgentSecurityEnforcementPolicy> findSecurityEnforcementPolicy(String agentId) {
        return Optional.ofNullable(dao.findSecurityEnforcementPolicy(agentId)).map(converter::toSecurityEnforcementPolicy);
    }

    @Override
    public List<AgentSecurityEnforcementPolicy> searchSecurityEnforcementPolicies(int limit) {
        return dao.searchSecurityEnforcementPolicies(limit).stream().map(converter::toSecurityEnforcementPolicy).toList();
    }

    @Override
    public List<AgentGovernanceTableDiagnostic> tableDiagnostics() {
        return dao.tableDiagnostics();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }
}
